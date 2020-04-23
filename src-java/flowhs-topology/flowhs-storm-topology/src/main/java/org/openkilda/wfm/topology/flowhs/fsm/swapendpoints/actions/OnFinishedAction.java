/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.SwapFlowResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsContext;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
public class OnFinishedAction extends NbTrackableAction<FlowSwapEndpointsFsm, State, Event, FlowSwapEndpointsContext> {

    public OnFinishedAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowSwapEndpointsContext context,
                                                    FlowSwapEndpointsFsm stateMachine) {
        log.info("Swap endpoints operation completed successfully.");

        stateMachine.saveFlowActionToHistory(stateMachine.getFirstFlowId(),
                format("Swap endpoints with flow %s was successful", stateMachine.getSecondFlowId()));
        stateMachine.saveFlowActionToHistory(stateMachine.getSecondFlowId(),
                format("Swap endpoints with flow %s was successful", stateMachine.getFirstFlowId()));

        List<FlowResponse> flowResponses = stateMachine.getFlowResponses();
        SwapFlowResponse response = stateMachine.getFirstFlowId().equals(flowResponses.get(0).getPayload().getFlowId())
                ? new SwapFlowResponse(flowResponses.get(0), flowResponses.get(1))
                : new SwapFlowResponse(flowResponses.get(1), flowResponses.get(0));
        CommandContext commandContext = stateMachine.getCommandContext();
        return Optional.of(new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId()));
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap endpoints";
    }
}
