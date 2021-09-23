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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.function.Consumer;

@Getter
@Setter
@Slf4j
public abstract class FlowProcessingWithEventSupportFsm<T extends NbTrackableFsm<T, S, E, C, R>, S, E, C,
        R extends FlowGenericCarrier, L extends FlowProcessingEventListener> extends NbTrackableFsm<T, S, E, C, R> {

    private final Collection<L> eventListeners;

    public FlowProcessingWithEventSupportFsm(CommandContext commandContext, @NonNull R carrier,
                                             boolean allowNorthboundResponse, @NonNull Collection<L> eventListeners) {
        super(commandContext, carrier, allowNorthboundResponse);
        this.eventListeners = eventListeners;
    }

    public void notifyEventListeners(Consumer<L> eventProducer) {
        eventListeners.forEach(eventProducer);
    }

    public void notifyEventListenersOnComplete() {
        notifyEventListeners(listener -> listener.onCompleted(getFlowId()));
    }

    public void notifyEventListenersOnError(ErrorType errorType, String errorMessage) {
        notifyEventListeners(listener ->
                listener.onFailed(getFlowId(), errorMessage, errorType));
    }
}
