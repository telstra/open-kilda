/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.isllatency.service;

import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.model.Endpoint;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class CacheService {
    private CacheCarrier carrier;
    private IslRepository islRepository;
    private Map<Endpoint, Endpoint> cache;

    public CacheService(CacheCarrier carrier, RepositoryFactory repositoryFactory) {
        this.carrier = carrier;
        islRepository = repositoryFactory.createIslRepository();
        cache = new HashMap<>();
        initCache();
    }

    private void initCache() {
        cache.clear();
        try {
            islRepository.findAllActive()
                    .forEach(isl -> {
                        Endpoint source = Endpoint.of(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort());
                        Endpoint destination = Endpoint.of(isl.getDestSwitch().getSwitchId(), isl.getDestPort());

                        cache.put(source, destination);
                        cache.put(destination, source);
                    });
            log.debug("Isl latency cache: {}", cache);
            log.info("Isl latency cache: Initialized");
        } catch (Exception ex) {
            log.error("Error during isl latency cache initialization", ex);
        }
    }

    /**
     * Remove outdated data from cache.
     *
     * @param notification notification about ISL status update
     */
    public void handleUpdateCache(IslStatusUpdateNotification notification) {
        if (notification.getStatus() == IslStatus.MOVED) {
            Endpoint source = Endpoint.of(notification.getSrcSwitchId(), notification.getSrcPortNo());
            Endpoint destination = Endpoint.of(notification.getDstSwitchId(), notification.getDstPortNo());

            cache.remove(source);
            cache.remove(destination);

            log.info("Remove ISL {}_{} ===> {}_{} from isl latency cache",
                    notification.getSrcSwitchId(), notification.getSrcPortNo(),
                    notification.getDstSwitchId(), notification.getDstPortNo());
        }
    }

    /**
     * Get data about specified ISL from cache.
     *
     * @param tuple tuple
     * @param roundTripLatency round trip latency info data with source endpoint
     * @param timestamp latency timestamp
     */
    public void handleGetDataFromCache(Tuple tuple, IslRoundTripLatency roundTripLatency, long timestamp) {
        Endpoint source = Endpoint.of(roundTripLatency.getSrcSwitchId(), roundTripLatency.getSrcPortNo());

        Endpoint destination = cache.get(source);

        if (destination == null) {
            destination = updateCache(source);
        }

        if (destination != null) {
            carrier.emitCachedData(tuple, roundTripLatency, destination, timestamp);
        }
    }

    private Endpoint updateCache(Endpoint source) {
        Isl isl = getActiveIsl(source);

        if (isl == null) {
            return null;
        }

        Endpoint destination = Endpoint.of(isl.getDestSwitch().getSwitchId(), isl.getDestPort());

        cache.put(source, destination);
        cache.put(destination, source);

        return destination;
    }

    private Isl getActiveIsl(Endpoint source) {
        List<Isl> activeIsls = islRepository.findBySrcEndpoint(source.getDatapath(), source.getPortNumber())
                .stream()
                .filter(isl -> isl.getStatus() == IslStatus.ACTIVE)
                .collect(Collectors.toList());

        if (activeIsls.isEmpty()) {
            log.warn("There is no active ISLs with src endpoint {}_{}.",
                    source.getDatapath(), source.getPortNumber());
            return null;
        }
        if (activeIsls.size() > 1) {
            List<String> destinationEndpoints = activeIsls.stream()
                    .map(isl -> String.format("%s_%d", isl.getDestSwitch(), isl.getDestPort()))
                    .collect(Collectors.toList());

            log.error("There is more than one active ISLs with source endpoint {}_{}. Destination endpoints: {}",
                    source.getDatapath(), source.getPortNumber(), destinationEndpoints);
            return null;
        }

        return activeIsls.get(0);
    }
}
