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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithEventSupportFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

@Slf4j
public abstract class FlowProcessingWithEventSupportService
        <T extends FlowProcessingWithEventSupportFsm<T, ?, E, C, R, L>, E, C, R extends FlowGenericCarrier,
                L extends FlowProcessingEventListener> extends FsmBasedFlowProcessingService<T, E, C, R> {
    protected final Set<L> eventListeners = new HashSet<>();

    public FlowProcessingWithEventSupportService(FsmExecutor<T, ?, E, C> fsmExecutor, R carrier,
                                                 PersistenceManager persistenceManager) {
        super(fsmExecutor, carrier, persistenceManager);
    }

    public void addEventListener(L eventListener) {
        eventListeners.add(eventListener);
    }
}
