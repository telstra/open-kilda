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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveYFlowAction extends YFlowProcessingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    public RemoveYFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    public void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        yFlowRepository.remove(stateMachine.getYFlowId());
        stateMachine.saveActionToHistory("The y-flow was removed");
    }
}
