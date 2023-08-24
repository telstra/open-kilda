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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.HistoryUpdateCarrier;
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.BaseHaFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteFlowPathRemovalAction extends
        BaseHaFlowPathRemovalAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    public CompleteFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @TimedExecution("fsm.complete_ha_flow_path_remove")
    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        HistoryUpdateCarrier carrier = stateMachine.getCarrier();
        String correlationId = stateMachine.getCommandContext().getCorrelationId();

        removeFlowPaths(stateMachine.getOldPrimaryPathIds(), carrier, correlationId);
        removeFlowPaths(stateMachine.getOldProtectedPathIds(), carrier, correlationId);
        removeRejectedPaths(stateMachine.getRejectedSubPathsIds(), stateMachine.getRejectedHaPathsIds(),
                carrier, correlationId);
    }
}
