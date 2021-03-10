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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class IslCacheService {

    private long islRttLatencyExpiration;
    private Map<Link, LinkState> linkStates;

    public IslCacheService(PersistenceManager persistenceManager, long islRttLatencyExpiration) {
        this.islRttLatencyExpiration = islRttLatencyExpiration;

        IslRepository islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        linkStates = islRepository.findAll().stream()
                .collect(Collectors.toMap(LinkMapper.INSTANCE::toLink, (link) -> LinkState.builder().build()));
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
        long currentTimeMillis = System.currentTimeMillis();
        links.forEach(link -> {
            LinkState linkState = linkStates.get(link);
            if (linkState == null) {
                linkStates.put(link, LinkState.builder()
                        .rttLatency(data.getLatency())
                        .rttTimestamp(currentTimeMillis)
                        .build());
            } else {
                linkState.setRttLatency(data.getLatency());
                linkState.setRttTimestamp(currentTimeMillis);
            }
        });
    }

    /**
     * Calculate latency by path.
     */
    public long calculateLatencyForPath(List<Link> path) {
        long currentTimeMillis = System.currentTimeMillis();
        return path.stream()
                .map(link -> linkStates.get(link))
                .filter(Objects::nonNull)
                .mapToLong(linkState -> linkState.getLatency(currentTimeMillis, islRttLatencyExpiration))
                .sum();
    }

    /**
     * Update ISL cache according to Isl changed info.
     */
    public void handleIslChangedData(IslChangedInfoData data) {
        cleanUpLinkStatesByEndpoint(data.getSource().getDatapath(), data.getSource().getPortNumber());
        if (data.getDestination() != null) {
            // Handle moved ISL
            cleanUpLinkStatesByEndpoint(data.getDestination().getDatapath(), data.getDestination().getPortNumber());
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
                .filter(link -> link.srcEquals(switchId, port)
                        || link.destEquals(switchId, port))
                .collect(Collectors.toList())
                .forEach(link -> linkStates.remove(link));
    }
}
