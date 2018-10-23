/* Copyright 2018 Telstra Open Source
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
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPair.FlowPairBuilder;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowService {
    private PersistenceManager persistenceManager;
    private FlowRepository flowRepository;
    private FlowSegmentRepository flowSegmentRepository;
    private SwitchRepository switchRepository;
    private IslRepository islRepository;

    public FlowService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        flowSegmentRepository = persistenceManager.getRepositoryFactory().createFlowSegmentRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
    }

    public Iterable<Flow> getFlow(String flowId) {
        return flowRepository.findById(flowId);
    }

    /**
     * Stores flow and it's segments into DB.
     *
     * @param flowPair - forward and reverse flows to be saved.
     */
    public void createFlow(FlowPair flowPair) {
        persistenceManager.getTransactionManager().begin();
        try {
            createFlowForPair(flowPair);
            persistenceManager.getTransactionManager().commit();
        } catch (Exception e) {
            persistenceManager.getTransactionManager().rollback();
        }
    }

    private void createFlowForPair(FlowPair flowPair) {
        Flow flow = flowPair.getForward();
        flowSegmentRepository.deleteFlowSegments(flow);
        processCreateFlow(flow);
        flow = flowPair.getReverse();
        processCreateFlow(flow);
    }

    private void processCreateFlow(Flow flow) {
        Switch srcSwitch = switchRepository.findBySwitchId(flow.getSrcSwitchId());
        Switch dstSwitch = switchRepository.findBySwitchId(flow.getDestSwitchId());
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(dstSwitch);
        flow.setLastUpdated(Instant.now());
        flowRepository.createOrUpdate(flow);
        flowSegmentRepository.mergeFlowSegments(flow);
        islRepository.updateIslBandwidth(flow);
    }

    /**
     * Deletes flow and it's segments from DB.
     *
     * @param flowId - flow id to be removed.
     */
    public void deleteFlow(String flowId) {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        transactionManager.begin();
        try {
            Iterable<Flow> flows = flowRepository.findById(flowId);
            for (Flow flow : flows) {
                processDeleteFlow(flow);
            }
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
        }
    }

    /**
     * Get FlowPair by the FlowId.
     * @param flowId - flow identificator
     * @return FlowPair object
     */
    public FlowPair getFlowPair(String flowId) {
        Iterable<Flow> flows = flowRepository.findById(flowId);
        FlowPair.FlowPairBuilder flowPairBuilder = FlowPair.builder();
        for (Flow flow: flows) {
            if (flow.isForward()) {
                flowPairBuilder.forward(flow);
            } else {
                flowPairBuilder.reverse(flow);
            }
        }
        return flowPairBuilder.build();
    }

    /**
     * Get all flows grouped in FlowPairs.
     * @return List of flow pairs
     */
    public List<FlowPair> getFlows() {
        Collection<Flow> flows = flowRepository.findAll();
        Map<String, FlowPairBuilder> flowPairs = new HashMap<>();
        for (Flow f : flows) {
            String flowId = f.getFlowId();
            FlowPair.FlowPairBuilder flowPairBuilder;
            if (flowPairs.containsKey(flowId)) {
                flowPairBuilder = flowPairs.get(flowId);
            } else {
                flowPairBuilder = FlowPair.builder();
                flowPairs.put(flowId, flowPairBuilder);
            }
            if (f.isForward()) {
                flowPairBuilder.forward(f);
            } else {
                flowPairBuilder.reverse(f);
            }

        }
        List<FlowPair> flowPairList = new ArrayList<>();
        for (FlowPairBuilder builder :flowPairs.values()) {
            flowPairList.add(builder.build());
        }
        return flowPairList;
    }

    /**
     * Update status for selected flow.
     * @param flowId - target flow to update
     * @param flowStatus - new status
     * @return target FlowPair
     */
    public FlowPair updateFlowStatus(String flowId, FlowStatus flowStatus) {
        TransactionManager transactionManager = this.persistenceManager.getTransactionManager();
        transactionManager.begin();
        try {
            FlowPair pair = getFlowPair(flowId);
            Flow forward = pair.getForward();
            Flow reverse = pair.getReverse();
            if (forward != null && reverse != null) {
                forward.setStatus(flowStatus);
                flowRepository.createOrUpdate(forward);

                reverse.setStatus(flowStatus);
                flowRepository.createOrUpdate(reverse);
            }
            transactionManager.commit();
            return pair;
        } catch (Exception e) {
            transactionManager.rollback();
            return null;
        }
    }

    private void deleteFlowForPair(FlowPair flowPair) {
        Flow flow = flowPair.getForward();
        processDeleteFlow(flow);
        flow = flowPair.getReverse();
        processDeleteFlow(flow);
    }

    private void processDeleteFlow(Flow flow) {
        flowSegmentRepository.deleteFlowSegments(flow);
        flowRepository.delete(flow);
    }

    /**
     * Replace existing flow in DB with the new one with similar id and updated info.
     *
     * @param flowPair - forward and reverse pairs to be processed
     */
    public void updateFlow(FlowPair flowPair) {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        transactionManager.begin();
        try {
            Flow flow = flowPair.getForward();
            flowSegmentRepository.deleteFlowSegments(flow);
            flowRepository.deleteByFlowId(flow.getFlowId());
            createFlowForPair(flowPair);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
        }
    }
}
