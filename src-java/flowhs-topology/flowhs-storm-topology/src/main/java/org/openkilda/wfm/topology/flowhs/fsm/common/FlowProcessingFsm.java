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

import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.flowhs.service.common.ProcessingEventListener;

import lombok.Getter;
import lombok.NonNull;
import org.squirrelframework.foundation.fsm.StateMachine;

import java.util.Collection;
import java.util.function.Consumer;

public abstract class FlowProcessingFsm<T extends StateMachine<T, S, E, C>, S, E, C, L extends ProcessingEventListener>
        extends AbstractBaseFsm<T, S, E, C> {
    private final E nextEvent;
    private final E errorEvent;
    @Getter
    private final Collection<L> eventListeners;
    @Getter
    private boolean writeErrorToHistory;
    @Getter
    private String errorReason;

    protected FlowProcessingFsm(@NonNull E nextEvent, @NonNull E errorEvent, @NonNull Collection<L> eventListeners) {
        this.nextEvent = nextEvent;
        this.errorEvent = errorEvent;
        this.eventListeners = eventListeners;
        this.writeErrorToHistory = true;
    }

    public void notifyEventListeners(@NonNull Consumer<L> eventProducer) {
        eventListeners.forEach(eventProducer);
    }

    public abstract String getFlowId();

    public void fireNext() {
        fireNext(null);
    }

    public void fireNext(C context) {
        fire(nextEvent, context);
    }

    public void fireError() {
        fireError((C) null);
    }

    public void fireError(C context) {
        fire(errorEvent, context);
    }

    public void fireError(String errorReason) {
        fireError(errorReason, true);
    }

    public void fireError(String errorReason, boolean writeErrorToHistory) {
        setErrorReason(errorReason);
        this.writeErrorToHistory = writeErrorToHistory;
        fireError();
    }

    public void fireError(@NonNull E eventToFire, String errorReason) {
        setErrorReason(errorReason);
        fire(eventToFire);
    }

    public void setErrorReason(String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: {}", errorReason);
        } else {
            this.errorReason = errorReason;
        }
    }
}
