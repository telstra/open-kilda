/* Copyright 2022 Telstra Open Source
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

import static org.openkilda.server42.messaging.FlowDirection.FORWARD;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStats;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowStatsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Service responsible for writing flow stats into database.
 */
@Slf4j
public class FlowStatsService {

    private final FlowRepository flowRepository;
    private final FlowStatsRepository flowStatsRepository;
    private final TransactionManager transactionManager;

    public FlowStatsService(PersistenceManager persistenceManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
        flowStatsRepository = repositoryFactory.createFlowStatsRepository();
        transactionManager = persistenceManager.getTransactionManager();
    }

    /**
     * Persist flow stats.
     */
    public void persistFlowStats(String flowId, String direction, long latency) {
        try {
            transactionManager.doInTransaction(() -> {
                FlowStats flowStats = flowStatsRepository.findByFlowId(flowId).orElse(null);
                if (flowStats == null) {
                    Optional<Flow> flow = flowRepository.findById(flowId);
                    if (flow.isPresent()) {
                        FlowStats toCreate = new FlowStats(flow.get(), null, null);
                        flowStatsRepository.add(toCreate);
                        flowStats = toCreate;
                    } else {
                        log.warn("Can't save latency for flow '{}'. Flow not found.", flowId);
                        return;
                    }
                }
                if (FORWARD.name().toLowerCase().equals(direction)) {
                    flowStats.setForwardLatency(latency);
                } else {
                    flowStats.setReverseLatency(latency);
                }
            });
        } catch (PersistenceException e) {
            log.error("Can't save latency for flow '{}'.", flowId, e);
        }
    }
}
