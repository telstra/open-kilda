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

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowEventListener;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import com.google.common.io.BaseEncoding;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public abstract class YFlowProcessingService<T extends FlowProcessingFsm<T, ?, E, C, YFlowEventListener>, E, C,
        R extends NorthboundResponseCarrier & LifecycleEventCarrier>
        extends FlowProcessingService<T, E, C, R, FlowProcessingFsmRegister<T>, YFlowEventListener> {
    private final Map<String, String> subFlowToYFlowMap = new HashMap<>();
    private final NoArgGenerator flowIdGenerator = Generators.timeBasedGenerator();

    protected YFlowProcessingService(@NonNull FsmExecutor<T, ?, E, C> fsmExecutor,
                                     @NonNull R carrier,
                                     @NonNull PersistenceManager persistenceManager) {
        super(new FlowProcessingFsmRegister<>(), fsmExecutor, carrier, persistenceManager);

        addEventListener(buildYFlowEventListener());
    }

    protected Optional<T> getFsmBySubFlowId(@NonNull String flowId) {
        return Optional.ofNullable(subFlowToYFlowMap.get(flowId))
                .flatMap(fsmRegister::getFsmByFlowId);
    }

    private YFlowEventListener buildYFlowEventListener() {
        return new YFlowEventListener() {
            @Override
            public void onSubFlowProcessingStart(String yFlowId, String subFlowId) {
                subFlowToYFlowMap.put(subFlowId, yFlowId);
            }

            @Override
            public void onSubFlowProcessingFinished(String yFlowId, String subFlowId) {
                subFlowToYFlowMap.remove(subFlowId);
            }
        };
    }

    protected String generateFlowId(@NonNull String prefix) {
        byte[] uuidAsBytes = UUIDUtil.asByteArray(flowIdGenerator.generate());
        String uuidAsBase32 = BaseEncoding.base32().omitPadding().lowerCase().encode(uuidAsBytes);
        return prefix + uuidAsBase32;
    }
}
