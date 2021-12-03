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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action;

import static java.lang.String.format;

import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertYFlowAction extends YFlowProcessingAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    public RevertYFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        YFlowRequest originalFlow = stateMachine.getOriginalFlow();
        YFlowResources resources = stateMachine.getReallocatedResources();

        FlowStatus flowStatus = transactionManager.doInTransaction(() -> {
            YFlow yFlow = getYFlow(originalFlow.getYFlowId());
            revertFlow(yFlow, YFlowRequestMapper.INSTANCE.toYFlow(originalFlow), resources);
            return yFlow.getStatus();
        });

        stateMachine.saveActionToHistory(format("The y-flow was reverted. The status %s", flowStatus));
    }

    private void revertFlow(YFlow yFlow, YFlow originalFlow, YFlowResources resources) {
        yFlow.setSharedEndpoint(originalFlow.getSharedEndpoint());
        yFlow.setMaximumBandwidth(originalFlow.getMaximumBandwidth());
        yFlow.setPathComputationStrategy(originalFlow.getPathComputationStrategy());
        yFlow.setEncapsulationType(originalFlow.getEncapsulationType());
        yFlow.setMaxLatency(originalFlow.getMaxLatency());
        yFlow.setMaxLatencyTier2(originalFlow.getMaxLatencyTier2());
        yFlow.setIgnoreBandwidth(originalFlow.isIgnoreBandwidth());
        yFlow.setPeriodicPings(originalFlow.isPeriodicPings());
        yFlow.setPinned(originalFlow.isPinned());
        yFlow.setPriority(originalFlow.getPriority());
        yFlow.setStrictBandwidth(originalFlow.isStrictBandwidth());
        yFlow.setDescription(originalFlow.getDescription());
        yFlow.setAllocateProtectedPath(originalFlow.isAllocateProtectedPath());
        yFlow.setStatus(FlowStatus.IN_PROGRESS);
        if (resources.getMainPathYPointResources() != null) {
            yFlow.setYPoint(resources.getMainPathYPointResources().getEndpoint());
            yFlow.setMeterId(resources.getMainPathYPointResources().getMeterId());
        }
        if (resources.getProtectedPathYPointResources() != null) {
            yFlow.setProtectedPathYPoint(resources.getProtectedPathYPointResources().getEndpoint());
            yFlow.setProtectedPathMeterId(resources.getProtectedPathYPointResources().getMeterId());
        }
        if (resources.getSharedEndpointResources() != null) {
            yFlow.setSharedEndpointMeterId(resources.getSharedEndpointResources().getMeterId());
        }
    }
}
