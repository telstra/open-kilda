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
import org.openkilda.model.Switch;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import java.time.Instant;

public class FlowService {
    private TransactionManager transactionManager;
    private FlowRepository flowRepository;
    private FlowSegmentRepository flowSegmentRepository;
    private SwitchRepository switchRepository;
    private IslRepository islRepository;

    public FlowService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        flowRepository = repositoryFactory.createFlowRepository();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        islRepository = repositoryFactory.createIslRepository();
    }


    /**
     * Stores flow and it's segments into DB.
     *
     * @param flowPair - forward and reverse flows to be saved.
     */
    public void createFlow(FlowPair flowPair) {
        transactionManager.begin();
        try {
            createFlowForPair(flowPair);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
        }
    }

    private void createFlowForPair(FlowPair flowPair) {
        Flow flow = flowPair.getForward();
        processCreateFlow(flow);
        flow = flowPair.getReverse();
        processCreateFlow(flow);
    }

    private void processCreateFlow(Flow flow) {
        flowSegmentRepository.deleteFlowSegments(flow);
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
        transactionManager.begin();
        try {
            deleteFlowForPair(flowPair);
            createFlowForPair(flowPair);
        } catch (Exception e) {
            transactionManager.rollback();
        }
    }
}
