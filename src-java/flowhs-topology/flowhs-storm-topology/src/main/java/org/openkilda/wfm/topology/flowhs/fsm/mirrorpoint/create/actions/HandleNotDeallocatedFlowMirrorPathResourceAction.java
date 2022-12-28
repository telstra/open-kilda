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

import static java.lang.String.format;

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleNotDeallocatedFlowMirrorPathResourceAction
        extends HistoryRecordingAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    @Override
    public void perform(State from, State to, Event event, FlowMirrorPointCreateContext context,
                        FlowMirrorPointCreateFsm stateMachine) {
        if (stateMachine.getFlowResources() != null) {
            stateMachine.saveErrorToHistory("Failed to revert flow mirror path allocation",
                    format("Failed to revert resource %s allocation for mirror paths: %s and %s",
                            stateMachine.getFlowResources(), stateMachine.getForwardMirrorPathId(),
                            stateMachine.getReverseMirrorPathId()));
        }

        if (!stateMachine.isRulesInstalled()) {
            log.debug("No need to re-install rules");
            stateMachine.fire(Event.SKIP_INSTALLING_RULES);
        }
    }
}
