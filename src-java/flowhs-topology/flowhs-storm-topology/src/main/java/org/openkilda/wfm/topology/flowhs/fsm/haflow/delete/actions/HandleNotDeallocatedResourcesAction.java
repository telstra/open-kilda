/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions;

import static java.lang.String.format;

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleNotDeallocatedResourcesAction extends
        HistoryRecordingAction<HaFlowDeleteFsm, State, Event, HaFlowDeleteContext> {
    @Override
    public void perform(State from, State to, Event event, HaFlowDeleteContext context, HaFlowDeleteFsm stateMachine) {
        stateMachine.getHaFlowResources().forEach(resources ->
                stateMachine.saveErrorToHistory("Failed to deallocate resources",
                        format("Failed to deallocate resources: %s", resources)));
    }
}
