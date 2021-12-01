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
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public abstract class FsmBasedFlowProcessingService<T extends FlowProcessingFsm<T, ?, E, C, R>, E, C,
        R extends FlowGenericCarrier> extends FlowProcessingService<R> {
    private final Map<String, T> fsmByKey = new HashMap<>();
    private final Map<String, String> keyByFlowId = new HashMap<>();
    protected final FsmExecutor<T, ?, E, C> fsmExecutor;

    @Getter(AccessLevel.PROTECTED)
    private boolean active;

    public FsmBasedFlowProcessingService(FsmExecutor<T, ?, E, C> fsmExecutor,
                                         R carrier, PersistenceManager persistenceManager) {
        super(carrier, persistenceManager);
        this.fsmExecutor = fsmExecutor;
    }

    protected void registerFsm(String key, T fsm) {
        fsmByKey.put(key, fsm);
        keyByFlowId.put(fsm.getFlowId(), key);
    }

    protected boolean hasRegisteredFsmWithKey(String key) {
        return fsmByKey.containsKey(key);
    }

    protected boolean hasRegisteredFsmWithFlowId(String flowId) {
        return keyByFlowId.containsKey(flowId);
    }

    protected boolean hasAnyRegisteredFsm() {
        return !fsmByKey.isEmpty();
    }

    protected Optional<T> getFsmByKey(String key) {
        return Optional.ofNullable(fsmByKey.get(key));
    }

    protected Optional<T> getFsmByFlowId(String flowId) {
        return getKeyByFlowId(flowId).map(fsmByKey::get);
    }

    protected Optional<String> getKeyByFlowId(String flowId) {
        return Optional.ofNullable(keyByFlowId.get(flowId));
    }

    protected void unregisterFsm(String key) {
        T removed = fsmByKey.remove(key);
        if (removed != null) {
            keyByFlowId.remove(removed.getFlowId());
        }
    }

    /**
     * Handles deactivate command.
     */
    public boolean deactivate() {
        active = false;
        return fsmByKey.isEmpty();
    }

    /**
     * Handles activate command.
     */
    public void activate() {
        active = true;
    }
}
