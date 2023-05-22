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

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class UpdateHaFlowAction extends
        NbTrackableWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public UpdateHaFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, HaFlowUpdateContext context,
                                                    HaFlowUpdateFsm stateMachine) {
        HaFlowRequest targetHaFlow = stateMachine.getTargetHaFlow();
        String haFlowId = targetHaFlow.getHaFlowId();

        transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(haFlowId);

            log.debug("Updating the flow {} with properties: {}", haFlowId, targetHaFlow);

            // Complete target ha-flow in FSM with values from original ha-flow
            stateMachine.setTargetHaFlow(updateFlow(haFlow, targetHaFlow));
        });

        stateMachine.saveActionToHistory("The flow properties were updated");
        return Optional.empty();
    }

    private HaFlowRequest updateFlow(HaFlow haFlow, HaFlowRequest targetHaFlow) {
        if (targetHaFlow.getDiverseFlowId() != null) {
            if (targetHaFlow.getDiverseFlowId().isEmpty()) {
                haFlow.setDiverseGroupId(null);
            } else {
                try {
                    getOrCreateFlowDiverseGroup(targetHaFlow.getDiverseFlowId()).ifPresent(haFlow::setDiverseGroupId);
                } catch (FlowNotFoundException e) {
                    throw new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Diverse flow %s not found", targetHaFlow.getDiverseFlowId()), e);
                }
            }
        } else if (targetHaFlow.isAllocateProtectedPath()) {
            if (haFlow.getDiverseGroupId() == null) {
                haFlow.setDiverseGroupId(haFlowRepository.getOrCreateDiverseHaFlowGroupId(haFlow.getHaFlowId())
                        .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                                format("Ha-flow %s not found", haFlow.getHaFlowId()))));
            }
        }

        Switch sharedSwitch = getSwitch(targetHaFlow.getSharedEndpoint().getSwitchId());
        haFlow.setSharedSwitch(sharedSwitch);
        haFlow.setSharedPort(targetHaFlow.getSharedEndpoint().getPortNumber());
        haFlow.setSharedOuterVlan(targetHaFlow.getSharedEndpoint().getOuterVlanId());
        haFlow.setSharedInnerVlan(targetHaFlow.getSharedEndpoint().getInnerVlanId());

        for (HaSubFlow haSubFlow : haFlow.getHaSubFlows()) {
            HaSubFlowDto targetHaSubFlow = targetHaFlow.getHaSubFlow(haSubFlow.getHaSubFlowId());
            haSubFlow.setEndpointSwitch(getSwitch(targetHaSubFlow.getEndpoint().getSwitchId()));
            haSubFlow.setEndpointPort(targetHaSubFlow.getEndpoint().getPortNumber());
            haSubFlow.setEndpointVlan(targetHaSubFlow.getEndpoint().getOuterVlanId());
            haSubFlow.setEndpointInnerVlan(targetHaSubFlow.getEndpoint().getInnerVlanId());
            haSubFlow.setDescription(targetHaSubFlow.getDescription());
        }
        haFlow.setPriority(targetHaFlow.getPriority());
        haFlow.setPinned(targetHaFlow.isPinned());
        haFlow.setAllocateProtectedPath(targetHaFlow.isAllocateProtectedPath());
        haFlow.setDescription(targetHaFlow.getDescription());
        haFlow.setMaximumBandwidth(targetHaFlow.getMaximumBandwidth());
        haFlow.setIgnoreBandwidth(targetHaFlow.isIgnoreBandwidth());
        haFlow.setStrictBandwidth(targetHaFlow.isStrictBandwidth());
        haFlow.setMaxLatency(targetHaFlow.getMaxLatency());
        haFlow.setMaxLatencyTier2(targetHaFlow.getMaxLatencyTier2());
        haFlow.setPeriodicPings(targetHaFlow.isPeriodicPings());
        if (targetHaFlow.getEncapsulationType() != null) {
            haFlow.setEncapsulationType(targetHaFlow.getEncapsulationType());
        } else {
            targetHaFlow.setEncapsulationType(haFlow.getEncapsulationType());
        }
        if (targetHaFlow.getPathComputationStrategy() != null) {
            haFlow.setPathComputationStrategy(targetHaFlow.getPathComputationStrategy());
        } else {
            targetHaFlow.setPathComputationStrategy(haFlow.getPathComputationStrategy());
        }
        return targetHaFlow;
    }

    protected String getGenericErrorMessage() {
        return "Couldn't update HA-flow";
    }
}
