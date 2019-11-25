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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.util.Collections.emptyMap;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowInstallingFsm;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerVerifySegmentEmitter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class EmitNonIngressRulesVerifyRequestsAction<T extends FlowInstallingFsm<T, S, E, C>, S, E, C>
        extends HistoryRecordingAction<T, S, E, C> {
    private final E skipEvent;

    public EmitNonIngressRulesVerifyRequestsAction(@NonNull E skipEvent) {
        this.skipEvent = skipEvent;
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        Collection<FlowSegmentRequestFactory> requestFactories = stateMachine.getNonIngressCommands().values();
        stateMachine.resetFailedCommandsAndRetries();

        if (requestFactories.isEmpty()) {
            stateMachine.setNonIngressCommands(emptyMap());
            stateMachine.resetPendingCommands();

            stateMachine.saveActionToHistory("No need to validate non ingress rules");

            stateMachine.fire(skipEvent);
        } else {
            Map<UUID, FlowSegmentRequestFactory> sentNonIngressRequests =
                    SpeakerVerifySegmentEmitter.INSTANCE.emitBatch(stateMachine.getCarrier(), requestFactories);
            stateMachine.setNonIngressCommands(sentNonIngressRequests);
            stateMachine.setPendingCommands(sentNonIngressRequests.keySet());

            stateMachine.saveActionToHistory("Started validation of installed non ingress rules");
        }
    }
}
