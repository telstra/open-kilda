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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerVerifySegmentEmitter;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class EmitVerifyRulesRequestsAction
        extends HistoryRecordingAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    @Override
    public void perform(State from, State to, Event event,
                        FlowMirrorPointCreateContext context, FlowMirrorPointCreateFsm stateMachine) {
        Map<UUID, FlowSegmentRequestFactory> requestsStorage = stateMachine.getCommands();
        if (requestsStorage.isEmpty()) {
            stateMachine.saveActionToHistory("No need to validate installed rules");

            stateMachine.fire(Event.RULES_VALIDATED);
        } else {
            ArrayList<FlowSegmentRequestFactory> requestFactories = new ArrayList<>(requestsStorage.values());
            requestsStorage.clear();

            SpeakerVerifySegmentEmitter.INSTANCE.emitBatch(
                    stateMachine.getCarrier(), requestFactories, requestsStorage);
            requestsStorage.forEach((key, value) -> stateMachine.getPendingCommands().put(key, value.getSwitchId()));
            stateMachine.getRetriedCommands().clear();

            stateMachine.saveActionToHistory("Started validation of installed rules");
        }
    }
}
