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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class CompleteFlowPathInstallationAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    public CompleteFlowPathInstallationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            if (stateMachine.getNewPrimaryForwardPath() != null && stateMachine.getNewPrimaryReversePath() != null) {
                PathId newForward = stateMachine.getNewPrimaryForwardPath();
                PathId newReverse = stateMachine.getNewPrimaryReversePath();

                log.debug("Completing installation of the flow path {} / {}", newForward, newReverse);
                flowPathRepository.updateStatus(newForward, FlowPathStatus.ACTIVE);
                flowPathRepository.updateStatus(newReverse, FlowPathStatus.ACTIVE);

                saveHistory(stateMachine, stateMachine.getFlowId(), newForward, newReverse);
            }

            if (stateMachine.getNewProtectedForwardPath() != null
                    && stateMachine.getNewProtectedReversePath() != null) {
                PathId newForward = stateMachine.getNewProtectedForwardPath();
                PathId newReverse = stateMachine.getNewProtectedReversePath();

                log.debug("Completing installation of the flow path {} / {}", newForward, newReverse);
                flowPathRepository.updateStatus(newForward, FlowPathStatus.ACTIVE);
                flowPathRepository.updateStatus(newReverse, FlowPathStatus.ACTIVE);

                saveHistory(stateMachine, stateMachine.getFlowId(), newForward, newReverse);
            }
        });
    }

    private void saveHistory(FlowRerouteFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Flow paths were installed")
                        .time(Instant.now())
                        .description(format("Flow paths %s/%s were installed",
                                forwardPath, reversePath))
                        .flowId(flowId)
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
