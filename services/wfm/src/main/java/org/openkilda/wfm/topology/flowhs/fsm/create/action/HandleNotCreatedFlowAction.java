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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class HandleNotCreatedFlowAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public HandleNotCreatedFlowAction(PersistenceManager persistenceManager,
                                      FlowOperationsDashboardLogger dashboardLogger) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        log.warn("Failed to create flow {}", flowId);

        dashboardLogger.onFlowStatusUpdate(flowId, FlowStatus.DOWN);
        flowRepository.updateStatus(flowId, FlowStatus.DOWN);
        flowPathRepository.updateStatus(stateMachine.getForwardPathId(), FlowPathStatus.INACTIVE);
        flowPathRepository.updateStatus(stateMachine.getReversePathId(), FlowPathStatus.INACTIVE);

        if (stateMachine.getProtectedForwardPathId() != null && stateMachine.getProtectedReversePathId() != null) {
            flowPathRepository.updateStatus(stateMachine.getProtectedForwardPathId(), FlowPathStatus.INACTIVE);
            flowPathRepository.updateStatus(stateMachine.getProtectedReversePathId(), FlowPathStatus.INACTIVE);
        }

        stateMachine.fire(Event.NEXT);
    }
}
