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

package org.openkilda.wfm.topology.flowhs.fsm;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Optional;

@Slf4j
public abstract class NbTrackableAction<T extends NbTrackableStateMachine<T, S, E, C>, S, E, C>
        extends AnonymousAction<T, S, E, C> {

    @Override
    public final void execute(S from, S to, E event, C context, T stateMachine) {
        Optional<Message> message = Optional.empty();
        try {
            message = perform(from, to, event, context, stateMachine);
        } catch (FlowProcessingException e) {
            CommandContext commandContext = stateMachine.getCommandContext();

            String errorMessage = format("%s: %s", e.getErrorMessage(), e.getErrorDescription());
            ErrorData error = new ErrorData(e.getErrorType(), errorMessage, e.getErrorDescription());
            message = Optional.of(new ErrorMessage(error, commandContext.getCreateTime(),
                    commandContext.getCorrelationId()));
            stateMachine.fireError();
        } catch (Exception e) {
            String errorMessage = "Failed to process flow request";
            log.error(errorMessage, e);
            ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, errorMessage, e.getMessage());
            message = Optional.of(new ErrorMessage(error, stateMachine.getCommandContext().getCreateTime(),
                    stateMachine.getCommandContext().getCorrelationId()));        
        } finally {
            message.ifPresent(stateMachine::sendResponse);
        }
    }

    protected abstract Optional<Message> perform(S from, S to, E event, C context, T stateMachine)
            throws FlowProcessingException;
}
