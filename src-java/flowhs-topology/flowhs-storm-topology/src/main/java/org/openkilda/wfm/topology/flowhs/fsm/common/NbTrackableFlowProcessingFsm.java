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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.service.common.NorthboundResponseCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.ProcessingEventListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.Collection;

public abstract class NbTrackableFlowProcessingFsm<T extends AbstractStateMachine<T, S, E, C>, S, E, C,
        R extends NorthboundResponseCarrier, L extends ProcessingEventListener>
        extends FlowProcessingFsm<T, S, E, C, L> {
    @Getter
    private final CommandContext commandContext;
    @Getter
    private final R carrier;
    @Getter
    @Setter
    private Message operationResultMessage;

    protected NbTrackableFlowProcessingFsm(@NonNull E nextEvent, @NonNull E errorEvent,
                                           @NonNull CommandContext commandContext, @NonNull R carrier,
                                           @NonNull Collection<L> eventListeners) {
        super(nextEvent, errorEvent, eventListeners);
        this.commandContext = commandContext;
        this.carrier = carrier;
    }

    public void sendNorthboundResponse(@NonNull Message message) {
        carrier.sendNorthboundResponse(message);
    }

    public void sendNorthboundError(ErrorType errorType, String errorMessage, String errorDescription) {
        Message message = buildErrorMessage(errorType, errorMessage, errorDescription);
        setOperationResultMessage(message);
        sendNorthboundResponse(message);
    }

    public Message buildErrorMessage(ErrorType errorType, String errorMessage, String errorDescription) {
        ErrorData error = new ErrorData(errorType, errorMessage, errorDescription);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

}
