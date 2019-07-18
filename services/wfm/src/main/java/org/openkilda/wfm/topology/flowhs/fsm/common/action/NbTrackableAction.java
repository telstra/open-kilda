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

package org.openkilda.wfm.topology.flowhs.fsm.common.action;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableStateMachine;
import org.openkilda.wfm.topology.flowhs.service.FlowHistorySupportingCarrier;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;
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
            String errorMessage = format("%s: %s", e.getErrorMessage(), e.getErrorDescription());
            log.info(errorMessage);
            message = Optional.of(
                    buildErrorMessage(stateMachine, e.getErrorType(), errorMessage, e.getErrorDescription()));
            stateMachine.fireError();
        } catch (Exception e) {
            log.error(getGenericErrorMessage(), e);
            message = Optional.of(buildErrorMessage(stateMachine, ErrorType.INTERNAL_ERROR,
                    getGenericErrorMessage(), e.getMessage()));
            stateMachine.fireError();
        } finally {
            message.ifPresent(stateMachine::sendResponse);
        }
    }

    protected Message buildErrorMessage(T stateMachine, ErrorType errorType,
                                        String errorMessage, String errorDescription) {
        CommandContext commandContext = stateMachine.getCommandContext();
        ErrorData error = new ErrorData(errorType, errorMessage, errorDescription);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    protected abstract Optional<Message> perform(S from, S to, E event, C context, T stateMachine);

    /**
     * Returns a message for generic error that may happen during action execution.
     * The message is being returned as the execution result.
     */
    protected String getGenericErrorMessage() {
        return "Failed to process flow request";
    }

    protected void saveHistory(T stateMachine, FlowHistorySupportingCarrier carrier, String flowId, String action) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(Instant.now())
                        .flowId(flowId)
                        .build())
                .build();
        carrier.sendHistoryUpdate(historyHolder);
    }
}
