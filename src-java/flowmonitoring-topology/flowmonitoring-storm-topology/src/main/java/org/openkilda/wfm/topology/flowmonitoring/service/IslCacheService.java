/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.service;

import org.openkilda.messaging.info.event.IslChangedInfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.topology.flowmonitoring.mapper.LinkMapper;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;
import org.openkilda.wfm.topology.flowmonitoring.model.LinkState;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class IslCacheService {

    private Clock clock;
    private Duration islRttLatencyExpiration;
    private Map<Link, LinkState> linkStates;

    public IslCacheService(PersistenceManager persistenceManager, Clock clock, Duration islRttLatencyExpiration) {
        this.clock = clock;
        this.islRttLatencyExpiration = islRttLatencyExpiration;

        IslRepository islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        initCache(islRepository);
    }

    private void initCache(IslRepository islRepository) {
        try {
            linkStates = islRepository.findAll().stream()
                    .collect(Collectors.toMap(LinkMapper.INSTANCE::toLink, (link) -> LinkState.builder().build()));
            log.info("Isl cache initialized successfully.");
        } catch (Exception e) {
            log.error("Isl cache initialization exception. Empty cache is used.", e);
            linkStates = new HashMap<>();
        }
    }

    /**
     * Update one way latency for ISL.
     */
    public void handleOneWayLatency(IslOneWayLatency data) {
        Link link = Link.builder()
                .srcSwitchId(data.getSrcSwitchId())
                .srcPort(data.getSrcPortNo())
                .destSwitchId(data.getDstSwitchId())
                .destPort(data.getDstPortNo())
                .build();
        LinkState linkState = linkStates.get(link);
        if (linkState == null) {
            linkStates.put(link, LinkState.builder()
                    .oneWayLatency(data.getLatency())
                    .build());
        } else {
            linkState.setOneWayLatency(data.getLatency());
        }
    }

    /**
     * Update RTT latency for ISL.
     */
    public void handleRoundTripLatency(IslRoundTripLatency data) {
        List<Link> links = linkStates.keySet().stream()
                .filter(link -> link.srcEquals(data.getSrcSwitchId(), data.getSrcPortNo()))
                .collect(Collectors.toList());
        Instant instant = clock.instant();
        links.forEach(link -> {
            LinkState linkState = linkStates.get(link);
            if (linkState == null) {
                linkStates.put(link, LinkState.builder()
                        .rttLatency(data.getLatency())
                        .rttTimestamp(instant)
                        .build());
            } else {
                linkState.setRttLatency(data.getLatency());
                linkState.setRttTimestamp(instant);
            }
        });
    }

    /**
     * Get latency for link.
     */
    public Duration getLatencyForLink(Link link) {
        log.debug("Request for link latency {}", link);
        LinkState linkState = linkStates.get(link);

        if (linkState == null) {
            log.warn("Link not found in ISL cache {}", link);
            return Duration.ZERO;
        } else {
            return linkState.getLatency(clock.instant(), islRttLatencyExpiration);
        }
    }

    /**
     * Update ISL cache according to Isl changed info.
     */
    public void handleIslChangedData(IslChangedInfoData data) {
        cleanUpLinkStatesByEndpoint(data.getSource().getDatapath(), data.getSource().getPortNumber());
        if (!data.isRemoved()) {
            // Handle moved or added ISL
            linkStates.put(Link.builder()
                            .srcSwitchId(data.getSource().getDatapath())
                            .srcPort(data.getSource().getPortNumber())
                            .destSwitchId(data.getDestination().getDatapath())
                            .destPort(data.getDestination().getPortNumber())
                            .build(),
                    LinkState.builder().build());
        }
    }

    private void cleanUpLinkStatesByEndpoint(SwitchId switchId, int port) {
        linkStates.keySet().stream()
                .filter(link -> link.srcEquals(switchId, port))
                .collect(Collectors.toList())
                .forEach(link -> linkStates.remove(link));
    }
}
