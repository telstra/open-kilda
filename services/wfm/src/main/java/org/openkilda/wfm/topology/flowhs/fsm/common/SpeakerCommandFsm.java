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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import static org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode.SWITCH_UNAVAILABLE;

import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateHubCarrier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

@Slf4j
@Getter
public final class SpeakerCommandFsm extends WithContextStateMachine<SpeakerCommandFsm, State, Event, FlowResponse> {

    private final SpeakerFlowRequest request;
    private final FlowCreateHubCarrier carrier;
    private int remainingRetries;

    private SpeakerCommandFsm(SpeakerFlowRequest request, FlowCreateHubCarrier carrier, Integer retriesLimit) {
        super(new CommandContext(request.getMessageContext().getCorrelationId()));
        this.request = request;
        this.carrier = carrier;
        this.remainingRetries = retriesLimit;
    }

    protected void processResponse(State from, State to, Event event, FlowResponse flowResponse) {
        if (flowResponse.isSuccess()) {
            log.debug("Successfully executed the command {}", flowResponse);
            fire(Event.NEXT);
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) flowResponse;
            if (errorResponse.getErrorCode() == SWITCH_UNAVAILABLE && remainingRetries-- > 0) {
                log.debug("About to retry execution of the command {}", flowResponse);
                fire(Event.RETRY);
            } else {
                log.info("Failed to execute the command {}", flowResponse);
                fire(Event.ERROR);
            }
        }
    }

    protected void sendCommand(State from, State to, Event event, FlowResponse flowResponse) {
        log.debug("Sending command {} to a speaker", request);
        carrier.sendSpeakerRequest(request);
    }

    @Override
    public void fireNext(FlowResponse context) {
        fire(Event.NEXT);
    }

    @Override
    public void fireError() {
        fire(Event.ERROR);
    }

    public static Builder getBuilder(FlowCreateHubCarrier carrier, int retriesLimit) {
        return new Builder(carrier, retriesLimit);
    }

    public enum Event {
        ACTIVATE,
        NEXT,
        REPLY,
        RETRY,
        TIMEOUT,
        ERROR
    }

    public enum State {
        INIT,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SUCCESS
    }

    public static final class Builder {
        private final FlowCreateHubCarrier carrier;
        private final int retriesLimit;
        private final StateMachineBuilder<SpeakerCommandFsm, State, Event, FlowResponse> builder;

        private Builder(FlowCreateHubCarrier carrier, int retriesLimit) {
            this.carrier = carrier;
            this.retriesLimit = retriesLimit;

            builder = StateMachineBuilderFactory.create(
                    SpeakerCommandFsm.class, State.class, Event.class, FlowResponse.class,
                    // extra params
                    SpeakerFlowRequest.class, FlowCreateHubCarrier.class, Integer.class
            );

            builder.transition()
                    .from(State.INIT)
                    .to(State.IN_PROGRESS)
                    .on(Event.ACTIVATE);

            builder.onEntry(State.IN_PROGRESS)
                    .callMethod("sendCommand");

            builder.transition()
                    .from(State.IN_PROGRESS)
                    .to(State.COMPLETED)
                    .on(Event.REPLY);

            builder.onEntry(State.COMPLETED)
                    .callMethod("processResponse");

            builder.transitions()
                    .from(State.COMPLETED)
                    .toAmong(State.SUCCESS, State.IN_PROGRESS, State.FAILED)
                    .onEach(Event.NEXT, Event.RETRY, Event.ERROR);

            builder.defineFinalState(State.SUCCESS);
            builder.defineFinalState(State.FAILED);
        }

        public SpeakerCommandFsm newInstance(SpeakerFlowRequest request) {
            return builder.newStateMachine(State.INIT, request, carrier, retriesLimit);
        }
    }
}
