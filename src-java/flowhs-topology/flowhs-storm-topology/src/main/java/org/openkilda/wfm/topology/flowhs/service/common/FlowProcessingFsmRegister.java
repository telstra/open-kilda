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

import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class FlowProcessingFsmRegister<T extends FlowProcessingFsm<T, ?, ?, ?, ?>> extends FsmRegister<String, T> {
    private final Map<String, String> keyByFlowId = new HashMap<>();

    @Override
    public void registerFsm(@NonNull String key, @NonNull T fsm) {
        super.registerFsm(key, fsm);
        keyByFlowId.put(fsm.getFlowId(), key);
    }

    public boolean hasRegisteredFsmWithFlowId(@NonNull String flowId) {
        return keyByFlowId.containsKey(flowId);
    }

    public Optional<T> getFsmByFlowId(@NonNull String flowId) {
        return getKeyByFlowId(flowId).flatMap(this::getFsmByKey);
    }

    public Optional<String> getKeyByFlowId(@NonNull String flowId) {
        return Optional.ofNullable(keyByFlowId.get(flowId));
    }

    @Override
    public T unregisterFsm(String key) {
        T removed = super.unregisterFsm(key);
        if (removed != null) {
            keyByFlowId.remove(removed.getFlowId());
        }
        return removed;
    }
}
