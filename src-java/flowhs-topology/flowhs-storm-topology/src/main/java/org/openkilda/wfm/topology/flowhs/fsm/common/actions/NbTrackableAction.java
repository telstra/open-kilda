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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public abstract class NbTrackableAction<T extends NbTrackableFsm<T, S, E, C, ?>, S, E, C>
        extends FlowProcessingAction<T, S, E, C> {

    public NbTrackableAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected final void perform(S from, S to, E event, C context, T stateMachine) {
        try {
            performWithResponse(from, to, event, context, stateMachine).ifPresent(stateMachine::sendNorthboundResponse);
        } catch (FlowProcessingException ex) {
            handleError(stateMachine, ex, ex.getErrorType(), false);
        } catch (Exception ex) {
            handleError(stateMachine, ex, ErrorType.INTERNAL_ERROR, true);
        }
    }

    protected abstract Optional<Message> performWithResponse(S from, S to, E event, C context, T stateMachine);

    protected Message buildErrorMessage(T stateMachine, ErrorType errorType,
                                        String errorMessage, String errorDescription) {
        CommandContext commandContext = stateMachine.getCommandContext();
        ErrorData error = new ErrorData(errorType, errorMessage, errorDescription);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    /**
     * Returns a message for generic error that may happen during action execution.
     * The message is being returned as the execution result.
     */
    protected abstract String getGenericErrorMessage();

    protected void handleError(T stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        String errorMessage = format("%s failed: %s", getClass().getSimpleName(), ex.getMessage());
        if (logTraceback) {
            stateMachine.saveErrorToHistory(errorMessage, ex);
        } else {
            stateMachine.saveErrorToHistory(errorMessage);
        }
        stateMachine.fireError(errorMessage);
        Message message = buildErrorMessage(stateMachine, errorType, getGenericErrorMessage(), ex.getMessage());
        stateMachine.setOperationResultMessage(message);
        stateMachine.sendNorthboundResponse(message);
    }
}
