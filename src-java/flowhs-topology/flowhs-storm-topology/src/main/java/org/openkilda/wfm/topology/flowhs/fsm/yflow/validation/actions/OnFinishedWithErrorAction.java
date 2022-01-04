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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class OnFinishedWithErrorAction extends
        NbTrackableAction<YFlowValidationFsm, State, Event, YFlowValidationContext> {
    @Override
    public Optional<Message> performWithResponse(State from, State to, Event event, YFlowValidationContext context,
                                                 YFlowValidationFsm stateMachine) {
        Message message = stateMachine.buildErrorMessage(ErrorType.INTERNAL_ERROR,
                format("Could not validate y-flow %s", stateMachine.getYFlowId()), stateMachine.getErrorReason());
        return Optional.of(message);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not validate flow";
    }
}
