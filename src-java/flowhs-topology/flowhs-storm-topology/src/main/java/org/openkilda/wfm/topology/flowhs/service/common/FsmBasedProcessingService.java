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

package org.openkilda.wfm.topology.flowhs.service.common;

import org.openkilda.wfm.share.utils.FsmExecutor;

import lombok.NonNull;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.HashSet;
import java.util.Set;

public abstract class FsmBasedProcessingService<T extends AbstractStateMachine<T, ?, E, C>, E, C,
        F extends FsmRegister<?, ?>, L extends ProcessingEventListener> extends FlowHsService {
    protected final F fsmRegister;
    protected final FsmExecutor<T, ?, E, C> fsmExecutor;
    protected final Set<L> eventListeners = new HashSet<>();

    protected FsmBasedProcessingService(@NonNull F fsmRegister,
                                        @NonNull FsmExecutor<T, ?, E, C> fsmExecutor) {
        this.fsmRegister = fsmRegister;
        this.fsmExecutor = fsmExecutor;
    }

    public void addEventListener(@NonNull L eventListener) {
        eventListeners.add(eventListener);
    }

    @Override
    public boolean deactivate() {
        super.deactivate();
        return !fsmRegister.hasAnyRegisteredFsm();
    }
}
