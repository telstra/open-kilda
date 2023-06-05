/* Copyright 2023 Telstra Open Source
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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class FlowStateCacheService {

    private Set<String> flows;

    public FlowStateCacheService(PersistenceManager persistenceManager) {
        FlowRepository flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        HaSubFlowRepository haSubFlowRepository = persistenceManager.getRepositoryFactory().createHaSubFlowRepository();
        initCache(flowRepository, haSubFlowRepository);
    }

    private void initCache(FlowRepository flowRepository, HaSubFlowRepository haSubFlowRepository) {
        try {
            flows = flowRepository.findAll().stream()
                    .filter(flow -> !flow.isOneSwitchFlow())
                    .filter(flow -> flow.getStatus() != FlowStatus.IN_PROGRESS)
                    .map(Flow::getFlowId)
                    .collect(Collectors.toSet());
            log.info("Flow state cache initialized successfully.");
        } catch (Exception e) {
            log.error("Flow state cache initialization exception. Empty cache is used.", e);
        }
        try {
            flows.addAll(haSubFlowRepository.findAll().stream()
                    .filter(haSubFlow -> haSubFlow.getHaFlow() != null)
                    .filter(haSubFlow -> !haSubFlow.isOneSwitch())
                    .filter(haSubFlow -> haSubFlow.getStatus() != FlowStatus.IN_PROGRESS)
                    .map(HaSubFlow::getHaSubFlowId)
                    .collect(Collectors.toSet()));
            log.info("HA-Sub-Flow state cache initialized successfully.");
        } catch (Exception e) {
            log.error("HA-flow state cache initialization exception. Empty cache is used.", e);
        }
    }

    /**
     * Add flow to cache.
     */
    public void updateFlow(String flowId) {
        flows.add(flowId);
    }

    /**
     * Remove flow from cache.
     */
    public void removeFlow(String flowId) {
        flows.remove(flowId);
    }

    public Set<String> getFlows() {
        return flows;
    }
}
