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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValidateYFlowResourcesAction extends
        YFlowProcessingWithHistorySupportAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    public ValidateYFlowResourcesAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        // We use validation directly in floodlight after installing the rules, so it's not necessary to do it here.
        stateMachine.fire(Event.ALL_YFLOW_METERS_VALIDATED);
    }
}
