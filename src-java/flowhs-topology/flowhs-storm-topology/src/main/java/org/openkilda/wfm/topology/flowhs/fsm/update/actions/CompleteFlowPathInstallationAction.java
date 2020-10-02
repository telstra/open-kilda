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

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteFlowPathInstallationAction extends
        FlowProcessingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    public CompleteFlowPathInstallationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        PathId newPrimaryForward = stateMachine.getNewPrimaryForwardPath();
        PathId newPrimaryReverse = stateMachine.getNewPrimaryReversePath();

        log.debug("Completing installation of the flow primary path {} / {}", newPrimaryForward, newPrimaryReverse);
        transactionManager.doInTransaction(() -> {
            flowPathRepository.updateStatus(newPrimaryForward, FlowPathStatus.ACTIVE);
            flowPathRepository.updateStatus(newPrimaryReverse, FlowPathStatus.ACTIVE);
        });

        stateMachine.saveActionToHistory("Flow paths were installed",
                format("The flow paths %s / %s were installed", newPrimaryForward, newPrimaryReverse));

        if (stateMachine.getNewProtectedForwardPath() != null
                && stateMachine.getNewProtectedReversePath() != null) {
            PathId newForward = stateMachine.getNewProtectedForwardPath();
            PathId newReverse = stateMachine.getNewProtectedReversePath();

            log.debug("Completing installation of the flow protected path {} / {}", newForward, newReverse);
            transactionManager.doInTransaction(() -> {
                flowPathRepository.updateStatus(newForward, FlowPathStatus.ACTIVE);
                flowPathRepository.updateStatus(newReverse, FlowPathStatus.ACTIVE);
            });

            stateMachine.saveActionToHistory("Flow paths were installed",
                    format("The flow paths %s / %s were installed", newForward, newReverse));
        }
    }
}
