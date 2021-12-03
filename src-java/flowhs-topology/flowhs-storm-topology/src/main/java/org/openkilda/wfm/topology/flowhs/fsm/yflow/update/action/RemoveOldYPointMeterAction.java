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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveOldYPointMeterAction extends
        YFlowProcessingAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    public RemoveOldYPointMeterAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        //TODO: build and send shared-endpoint and y-point (main & protected) meters remove command
        stateMachine.saveActionToHistory("No need to remove y-point meter");
        stateMachine.fire(Event.YPOINT_METER_REMOVED);
    }
}
