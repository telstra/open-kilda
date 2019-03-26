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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;

import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Optional;

public abstract class NbTrackableAction<T extends NbTrackableStateMachine<T, S, E, C>, S, E, C>
        extends AnonymousAction<T, S, E, C> {

    @Override
    public final void execute(S from, S to, E event, C context, T stateMachine) {
        try {
            Optional<Message> message = perform(from, to, event, context, stateMachine);
            if (message.isPresent()) {
                stateMachine.sendResponse(message.get());
            } else {
                stateMachine.fireNext(context);
            }
        } catch (FlowProcessingException e) {
            CommandContext commandContext = stateMachine.getCommandContext();
            ErrorData error = new ErrorData(e.getErrorType(), e.getErrorMessage(), e.getErrorDescription());
            Message message = new ErrorMessage(error, commandContext.getCreateTime(),
                    commandContext.getCorrelationId());
            stateMachine.sendResponse(message);
            stateMachine.fireError();
        }
    }

    protected abstract Optional<Message> perform(S from, S to, E event, C context, T stateMachine)
            throws FlowProcessingException;
}
