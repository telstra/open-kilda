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

package org.openkilda.northbound.service.impl;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.model.history.FlowDump;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.northbound.service.AuthService;
import org.openkilda.northbound.service.HistoryService;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;

@Service
public class HistoryServiceImpl implements HistoryService {

    @Value("${neo4j.uri}")
    private String neoUri;

    @Value("${neo4j.user}")
    private String neoUser;

    @Value("${neo4j.password}")
    private String neoPswd;

    @Autowired
    private AuthService authService;

    private org.openkilda.history.HistoryService historyService;

    @PostConstruct
    void init() {
        PersistenceManager persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> T getConfiguration(Class<T> configurationType) {
                        if (configurationType.equals(Neo4jConfig.class)) {
                            return (T) new Neo4jConfig() {
                                @Override
                                public String getUri() {
                                    return neoUri;
                                }

                                @Override
                                public String getLogin() {
                                    return neoUser;
                                }

                                @Override
                                public String getPassword() {
                                    return neoPswd;
                                }

                                @Override
                                public int getConnectionPoolSize() {
                                    return 50;
                                }
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                    + configurationType);
                        }
                    }
                });
        historyService = new org.openkilda.history.HistoryService(persistenceManager);
    }

    @Override
    public void logAction(String action, Future<FlowPayload> flow, String correlationId, String details) {
        historyService.store(FlowEvent.builder()
                .taskId(correlationId)
                .actor(authService.getUserName())
                .action(action)
                .timestamp(Instant.now())
                .details(details)
                .build());
        if (flow != null) {
            try {
                FlowPayload flowPayload = flow.get();
                historyService.store(FlowDump.builder()
                        .taskId(correlationId)
                        .flowId(flowPayload.getId())
                        .sourceSwitch(flowPayload.getSource().getDatapath())
                        .sourcePort(flowPayload.getSource().getPortNumber())
                        .sourceVlan(flowPayload.getSource().getVlanId())
                        .destinationSwitch(flowPayload.getDestination().getDatapath())
                        .destinationPort(flowPayload.getDestination().getPortNumber())
                        .destinationVlan(flowPayload.getDestination().getVlanId())
                        .bandwidth(flowPayload.getMaximumBandwidth())
                        .ignoreBandwidth(flowPayload.isIgnoreBandwidth())
                        .build(), "stateBefore");
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
