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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertFlowStatusAction extends FlowProcessingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    public RevertFlowStatusAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        FlowStatus originalFlowStatus = stateMachine.getOriginalFlowStatus();
        FlowStatus newFlowStatus = stateMachine.getNewFlowStatus();
        if (originalFlowStatus != null) {
            log.debug("Reverting the flow status of {} to {}", flowId, originalFlowStatus);

            String flowStatusInfo = FlowStatus.DEGRADED.equals(originalFlowStatus)
                    ? "Couldn't find non overlapping protected path" : stateMachine.getOriginalFlowStatusInfo();
            flowRepository.updateStatus(flowId, originalFlowStatus, flowStatusInfo);

            stateMachine.saveActionToHistory(format("The flow status was reverted to %s", originalFlowStatus));
        } else {
            String flowStatusInfo = !FlowStatus.UP.equals(newFlowStatus) ? stateMachine.getErrorReason() : null;
            flowRepository.updateStatusInfo(flowId, flowStatusInfo);
        }
    }
}
