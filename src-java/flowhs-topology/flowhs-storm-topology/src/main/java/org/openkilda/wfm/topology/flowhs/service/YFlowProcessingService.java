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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithEventSupportFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public abstract class YFlowProcessingService
        <T extends FlowProcessingWithEventSupportFsm<T, ?, E, C, R, YFlowEventListener>, E, C,
                R extends FlowGenericCarrier>
        extends FlowProcessingWithEventSupportService<T, E, C, R, YFlowEventListener> {
    private final Map<String, String> subFlowToYFlowMap = new HashMap<>();

    public YFlowProcessingService(FsmExecutor<T, ?, E, C> fsmExecutor, R carrier,
                                  PersistenceManager persistenceManager) {
        super(fsmExecutor, carrier, persistenceManager);
        addEventListener(buildYFlowEventListener());
    }

    protected Optional<T> getFsmBySubFlowId(String flowId) {
        return Optional.ofNullable(subFlowToYFlowMap.get(flowId))
                .flatMap(this::getFsmByFlowId);
    }

    private YFlowEventListener buildYFlowEventListener() {
        return new YFlowEventListener() {
            @Override
            public void onCompleted(String flowId) {
                // Nothing to do yet
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                // Nothing to do yet
            }

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
}
