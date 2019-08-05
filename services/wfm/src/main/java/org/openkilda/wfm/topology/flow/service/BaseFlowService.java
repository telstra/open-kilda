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

package org.openkilda.wfm.topology.flow.service;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPair;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.flow.model.FlowData;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class BaseFlowService {
    protected final FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);
    protected final FlowResourcesManager flowResourcesManager;
    protected final TransactionManager transactionManager;
    protected final FlowRepository flowRepository;
    protected final FlowPairRepository flowPairRepository;

    public BaseFlowService(PersistenceManager persistenceManager, FlowResourcesManager flowResourcesManager) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPairRepository = repositoryFactory.createFlowPairRepository();
        this.flowResourcesManager = flowResourcesManager;
    }

    public boolean doesFlowExist(String flowId) {
        return flowRepository.exists(flowId);
    }

    /**
     * Fetch a path pair for the flow.
     */
    public Optional<FlowPair> getFlowPair(String flowId) {
        dashboardLogger.onFlowRead(flowId);

        return flowPairRepository.findById(flowId);
    }

    /**
     * Finds flow data with flow group by specified flow id.
     * @param flowId flow identifier.
     * @return flow data with flow group.
     */
    public Optional<FlowData> getFlow(String flowId) {
        dashboardLogger.onFlowRead(flowId);
        return flowRepository.findById(flowId)
                .map(flow -> FlowData.builder()
                        .flowDto(FlowMapper.INSTANCE.map(flow))
                        .flowGroup(flow.getGroupId())
                        .build());
    }

    /**
     * Fetches all flows without flow groups.
     */
    public List<FlowData> getFlows() {
        dashboardLogger.onFlowDump();
        return flowRepository.findAll().stream()
                .map(FlowMapper.INSTANCE::map)
                .map(FlowData::new)
                .collect(Collectors.toList());
    }

    protected Optional<FlowPathsWithEncapsulation> getFlowPathPairWithEncapsulation(String flowId) {
        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        if (foundFlow.isPresent()) {
            Flow flow = foundFlow.get();
            FlowEncapsulationType encapsulationType = flow.getEncapsulationType();
            EncapsulationResources forwardEncapsulation = flowResourcesManager.getEncapsulationResources(
                    flow.getForwardPathId(), flow.getReversePathId(), encapsulationType).orElse(null);
            EncapsulationResources reverseEncapsulation = flowResourcesManager.getEncapsulationResources(
                    flow.getReversePathId(), flow.getForwardPathId(), encapsulationType).orElse(null);
            EncapsulationResources protectedForwardEncapsulation = flowResourcesManager.getEncapsulationResources(
                    flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(), encapsulationType).orElse(null);
            EncapsulationResources protectedReverseEncapsulation = flowResourcesManager.getEncapsulationResources(
                    flow.getProtectedReversePathId(), flow.getProtectedForwardPathId(), encapsulationType).orElse(null);
            return Optional.of(FlowPathsWithEncapsulation.builder()
                    .flow(flow)
                    .forwardPath(flow.getForwardPath())
                    .reversePath(flow.getReversePath())
                    .forwardEncapsulation(forwardEncapsulation)
                    .reverseEncapsulation(reverseEncapsulation)
                    .protectedForwardPath(flow.getProtectedForwardPath())
                    .protectedReversePath(flow.getProtectedReversePath())
                    .protectedForwardEncapsulation(protectedForwardEncapsulation)
                    .protectedReverseEncapsulation(protectedReverseEncapsulation)
                    .build());
        } else {
            return Optional.empty();
        }
    }
}
