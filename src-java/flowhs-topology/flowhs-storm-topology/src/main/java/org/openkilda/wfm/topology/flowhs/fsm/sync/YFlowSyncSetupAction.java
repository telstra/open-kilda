/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.sync;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.State;

import java.util.List;
import java.util.stream.Collectors;

public class YFlowSyncSetupAction extends SyncSetupActionBase<YFlowSyncFsm, State, Event, YFlow> {
    private final YFlowRepository yFlowRepository;

    public YFlowSyncSetupAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, dashboardLogger);
        this.yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
    }

    @Override
    protected void transaction(YFlowSyncFsm stateMachine, YFlow container) {
        ensureNoCollision(container);
        super.transaction(stateMachine, container);
    }

    @Override
    protected YFlow loadContainer(YFlowSyncFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        return yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Y-flow %s not found", yFlowId)));
    }

    @Override
    protected List<Flow> collectAffectedFlows(YFlow container) {
        return container.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .collect(Collectors.toList());
    }

    private void ensureNoCollision(YFlow yFlow) {
        if (yFlow.getStatus() == FlowStatus.IN_PROGRESS) {
            String message = format("Y-Flow %s is in progress now", yFlow.getYFlowId());
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID, message);
        }
    }
}
