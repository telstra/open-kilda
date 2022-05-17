/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FlowValidateAction extends
        NbTrackableWithHistorySupportAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {
    private final FlowRepository flowRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public FlowValidateAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.flowRepository = repositoryFactory.createFlowRepository();

        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowPathSwapContext context,
                                                    FlowPathSwapFsm stateMachine) throws FlowProcessingException {
        transactionManager.doInTransaction(() -> {
            String flowId = stateMachine.getFlowId();
            Flow flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Could not swap paths: Flow %s not found", flowId)));
            dashboardLogger.onFlowPathsSwap(flow);
            stateMachine.setPeriodicPingsEnabled(flow.isPeriodicPings());
            if (flow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Flow %s is in progress now", flowId));
            }
            if (!flow.isAllocateProtectedPath()
                    || flow.getProtectedForwardPath() == null || flow.getProtectedReversePath() == null) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Could not swap paths: Flow %s doesn't have protected path", flowId));
            }
            if (FlowPathStatus.ACTIVE != flow.getProtectedForwardPath().getStatus()
                    || FlowPathStatus.ACTIVE != flow.getProtectedReversePath().getStatus()) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Could not swap paths: Protected flow path %s is not in ACTIVE state", flowId));
            }
            stateMachine.setOldPrimaryForwardPath(flow.getForwardPathId());
            stateMachine.setOldPrimaryReversePath(flow.getReversePathId());
            stateMachine.setOldProtectedForwardPath(flow.getProtectedForwardPathId());
            stateMachine.setOldPrimaryReversePath(flow.getProtectedReversePathId());
            stateMachine.setOldPathStatuses(flow.getPaths());


            flow.setStatus(FlowStatus.IN_PROGRESS);

            flow.getForwardPath().setStatus(FlowPathStatus.IN_PROGRESS);
            flow.getReversePath().setStatus(FlowPathStatus.IN_PROGRESS);
            flow.getProtectedForwardPath().setStatus(FlowPathStatus.IN_PROGRESS);
            flow.getProtectedReversePath().setStatus(FlowPathStatus.IN_PROGRESS);

        });
        stateMachine.saveNewEventToHistory("Flow was validated successfully", FlowEventData.Event.PATH_SWAP);

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap paths for flow";
    }
}
