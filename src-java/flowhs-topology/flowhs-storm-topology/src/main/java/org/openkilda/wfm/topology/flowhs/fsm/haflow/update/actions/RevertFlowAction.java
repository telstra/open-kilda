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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class RevertFlowAction extends
        FlowProcessingWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public RevertFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
            revertFlow(haFlow, stateMachine.getOriginalHaFlow());

            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("The HA-flow has been reverted")
                    .withHaFlowId(stateMachine.getHaFlowId()));
        });
    }

    private void revertFlow(HaFlow haFlow, HaFlow originalHaFlow) {
        Switch sharedSwitch = getSwitch(originalHaFlow.getSharedEndpoint().getSwitchId());
        haFlow.setSharedSwitch(sharedSwitch);
        haFlow.setSharedPort(originalHaFlow.getSharedEndpoint().getPortNumber());
        haFlow.setSharedOuterVlan(originalHaFlow.getSharedEndpoint().getOuterVlanId());
        haFlow.setSharedInnerVlan(originalHaFlow.getSharedEndpoint().getInnerVlanId());

        for (HaSubFlow haSubFlow : haFlow.getHaSubFlows()) {
            Optional<HaSubFlow> originalHaSubFlow = originalHaFlow.getHaSubFlow(haSubFlow.getHaSubFlowId());
            if (!originalHaSubFlow.isPresent()) {
                log.error("Unknown subflow {} of ha-flow {}", haSubFlow.getHaSubFlowId(), originalHaFlow.getHaFlowId());
                continue;
            }
            haSubFlow.setEndpointSwitch(getSwitch(originalHaSubFlow.get().getEndpoint().getSwitchId()));
            haSubFlow.setEndpointPort(originalHaSubFlow.get().getEndpoint().getPortNumber());
            haSubFlow.setEndpointVlan(originalHaSubFlow.get().getEndpoint().getOuterVlanId());
            haSubFlow.setEndpointInnerVlan(originalHaSubFlow.get().getEndpoint().getInnerVlanId());
            haSubFlow.setDescription(originalHaSubFlow.get().getDescription());
        }

        haFlow.setPriority(originalHaFlow.getPriority());
        haFlow.setPinned(originalHaFlow.isPinned());
        haFlow.setAllocateProtectedPath(originalHaFlow.isAllocateProtectedPath());
        haFlow.setDescription(originalHaFlow.getDescription());
        haFlow.setMaximumBandwidth(originalHaFlow.getMaximumBandwidth());
        haFlow.setIgnoreBandwidth(originalHaFlow.isIgnoreBandwidth());
        haFlow.setStrictBandwidth(originalHaFlow.isStrictBandwidth());
        haFlow.setMaxLatency(originalHaFlow.getMaxLatency());
        haFlow.setMaxLatencyTier2(originalHaFlow.getMaxLatencyTier2());
        haFlow.setPeriodicPings(originalHaFlow.isPeriodicPings());
        haFlow.setEncapsulationType(originalHaFlow.getEncapsulationType());
        haFlow.setPathComputationStrategy(originalHaFlow.getPathComputationStrategy());
        haFlow.setPathComputationStrategy(originalHaFlow.getPathComputationStrategy());
        haFlow.setDiverseGroupId(originalHaFlow.getDiverseGroupId());
    }
}
