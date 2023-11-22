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

package org.openkilda.wfm.topology.flowhs.fsm.swapendpoints;

import static java.lang.String.format;

import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.CreateNewHistoryEventForSwapEndpointsAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.OnReceivedUpdateResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.RevertFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.RevertUpdateRequestAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.UpdateRequestAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions.ValidateFlowsAction;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowSwapEndpointsHubCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Slf4j
public class FlowSwapEndpointsFsm extends FlowProcessingWithHistorySupportFsm<FlowSwapEndpointsFsm, State, Event,
        FlowSwapEndpointsContext, FlowSwapEndpointsHubCarrier, FlowProcessingEventListener> {
    public static final int REQUEST_COUNT = 2;

    public static final String GENERIC_ERROR_MESSAGE = "Could not swap endpoints";

    private final RequestedFlow firstTargetFlow;
    private final RequestedFlow secondTargetFlow;
    private final String firstFlowId;
    private final String secondFlowId;

    private int awaitingResponses = REQUEST_COUNT;
    private List<FlowResponse> flowResponses = new ArrayList<>();
    private List<ErrorData> errors = new ArrayList<>();
    private Flow firstOriginalFlow;
    private Flow secondOriginalFlow;

    public FlowSwapEndpointsFsm(@NonNull CommandContext commandContext, @NonNull FlowSwapEndpointsHubCarrier carrier,
                                @NonNull RequestedFlow firstTargetFlow, @NonNull RequestedFlow secondTargetFlow) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier);
        this.firstTargetFlow = firstTargetFlow;
        this.secondTargetFlow = secondTargetFlow;
        this.firstFlowId = firstTargetFlow.getFlowId();
        this.secondFlowId = secondTargetFlow.getFlowId();
    }

    @Override
    public String getFlowId() {
        throw new UnsupportedOperationException("Not implemented for swap flow endpoints operation. Skipping");
    }

    @Override
    protected String[] getFlowIdsForHistory() {
        return new String[]{firstFlowId, secondFlowId};
    }

    @Override
    protected String getCrudActionName() {
        return "swap-endpoints";
    }

    /**
     * Fire VALIDATION_ERROR with ErrorData.
     */
    public void fireValidationError(ErrorData errorData) {
        fireError(errorData, Event.VALIDATION_ERROR);
    }

    @Override
    public void fireError(String errorReason) {
        fireError(new ErrorData(ErrorType.DATA_INVALID, GENERIC_ERROR_MESSAGE, errorReason));
    }

    /**
     * Fire ERROR with ErrorData.
     */
    public void fireError(ErrorData errorData) {
        fireError(errorData, Event.ERROR);
    }

    private void fireError(ErrorData errorData, Event event) {
        log.error("Flow swap endpoints failed: {}", errorData.getErrorDescription());
        FlowSwapEndpointsContext context = new FlowSwapEndpointsContext(errorData);
        fire(event, context);
    }

    /**
     * Fire if all responses received.
     */
    public void fireWhenResponsesReceived() {
        if (--awaitingResponses == 0) {
            if (flowResponses.size() == REQUEST_COUNT) {
                fireNext();
            } else {
                List<String> errorMessages = errors.stream()
                        .map(ErrorData::getErrorDescription)
                        .collect(Collectors.toList());
                String errorDescription = format("Reasons of the flow updates failures: %s", errorMessages);
                fireError(new ErrorData(ErrorType.UPDATE_FAILURE, GENERIC_ERROR_MESSAGE, errorDescription));
            }
        }
    }

    public void sendFlowUpdateRequest(FlowRequest flowRequest) {
        getCarrier().sendFlowUpdateRequest(flowRequest);
    }

    public static class Factory {
        private final StateMachineBuilder<FlowSwapEndpointsFsm, State, Event, FlowSwapEndpointsContext> builder;
        private final FlowSwapEndpointsHubCarrier carrier;

        public Factory(@NonNull FlowSwapEndpointsHubCarrier carrier, @NonNull PersistenceManager persistenceManager) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(FlowSwapEndpointsFsm.class, State.class, Event.class,
                    FlowSwapEndpointsContext.class, CommandContext.class, FlowSwapEndpointsHubCarrier.class,
                    RequestedFlow.class, RequestedFlow.class);

            builder.transition()
                    .from(State.CREATE_NEW_HISTORY_EVENT)
                    .to(State.INITIALIZED)
                    .on(Event.NEXT)
                    .perform(new CreateNewHistoryEventForSwapEndpointsAction(persistenceManager));

            builder.transition().from(State.INITIALIZED).to(State.FLOWS_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateFlowsAction(persistenceManager));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOWS_VALIDATED).to(State.SEND_UPDATE_REQUESTS).on(Event.NEXT)
                    .perform(new UpdateRequestAction());
            builder.transitions().from(State.FLOWS_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.VALIDATION_ERROR, Event.ERROR, Event.TIMEOUT);

            builder.internalTransition().within(State.SEND_UPDATE_REQUESTS).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedUpdateResponseAction());
            builder.internalTransition().within(State.SEND_UPDATE_REQUESTS).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedUpdateResponseAction());
            builder.transitions().from(State.SEND_UPDATE_REQUESTS)
                    .toAmong(State.FINISHED, State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.TIMEOUT);
            builder.transition()
                    .from(State.SEND_UPDATE_REQUESTS).to(State.SEND_REVERT_UPDATE_REQUESTS).on(Event.ERROR)
                    .perform(new RevertUpdateRequestAction());

            builder.internalTransition().within(State.SEND_REVERT_UPDATE_REQUESTS).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedUpdateResponseAction());
            builder.internalTransition().within(State.SEND_REVERT_UPDATE_REQUESTS).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedUpdateResponseAction());
            builder.transitions().from(State.SEND_REVERT_UPDATE_REQUESTS)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.REVERTING_FLOW_STATUS)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new RevertFlowStatusAction(persistenceManager));

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);
            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(persistenceManager, dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(persistenceManager, dashboardLogger));
        }

        public FlowSwapEndpointsFsm newInstance(@NonNull CommandContext commandContext,
                                                @NonNull RequestedFlow firstTargetFlow,
                                                @NonNull RequestedFlow secondTargetFlow) {
            return builder.newStateMachine(FlowSwapEndpointsFsm.State.CREATE_NEW_HISTORY_EVENT, commandContext, carrier,
                    firstTargetFlow, secondTargetFlow);
        }
    }

    public enum State {
        CREATE_NEW_HISTORY_EVENT,
        INITIALIZED,
        FLOWS_VALIDATED,
        SEND_UPDATE_REQUESTS,
        SEND_REVERT_UPDATE_REQUESTS,
        REVERTING_FLOW_STATUS,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum Event {
        NEXT,
        RESPONSE_RECEIVED,
        ERROR_RECEIVED,
        VALIDATION_ERROR,
        TIMEOUT,
        ERROR
    }
}
