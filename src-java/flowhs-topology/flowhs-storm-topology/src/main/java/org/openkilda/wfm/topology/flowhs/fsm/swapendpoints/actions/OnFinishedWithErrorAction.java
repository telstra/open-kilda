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
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.model.FlowDto;
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
import java.util.stream.Collectors;

@Slf4j
public class OnFinishedWithErrorAction
        extends NbTrackableAction<FlowSwapEndpointsFsm, State, Event, FlowSwapEndpointsContext> {

    public OnFinishedWithErrorAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowSwapEndpointsContext context,
                                                    FlowSwapEndpointsFsm stateMachine) {
        ErrorData data;
        if (Event.TIMEOUT.equals(event)) {
            data = new ErrorData(ErrorType.OPERATION_TIMED_OUT, getGenericErrorMessage(),
                    "Flow swap endpoints failed by timeout");
        } else if (Event.NEXT.equals(event)) {
            List<String> flows = stateMachine.getFlowResponses().stream()
                    .map(FlowResponse::getPayload)
                    .map(FlowDto::getFlowId)
                    .collect(Collectors.toList());
            data = new ErrorData(ErrorType.UPDATE_FAILURE, getGenericErrorMessage(),
                    format("Reverted flows: %s", flows));
        } else {
            data = (ErrorData) context.getResponse();
        }

        if (!Event.VALIDATION_ERROR.equals(event)) {
            saveActionToHistory(stateMachine,
                    stateMachine.getFirstFlowId(), stateMachine.getSecondFlowId(), data.getErrorDescription());
            saveActionToHistory(stateMachine,
                    stateMachine.getSecondFlowId(), stateMachine.getFirstFlowId(), data.getErrorDescription());
        }

        CommandContext commandContext = stateMachine.getCommandContext();
        return Optional.of(new ErrorMessage(data, commandContext.getCreateTime(), commandContext.getCorrelationId()));
    }

    private void saveActionToHistory(FlowSwapEndpointsFsm stateMachine,
                                     String firstFlowId, String secondFlowId, String errorDescription) {
        stateMachine.saveFlowActionToHistory(firstFlowId,
                format("Failed to swap endpoints with flow %s", secondFlowId), errorDescription);
    }

    @Override
    protected String getGenericErrorMessage() {
        return FlowSwapEndpointsFsm.GENERIC_ERROR_MESSAGE;
    }
}
