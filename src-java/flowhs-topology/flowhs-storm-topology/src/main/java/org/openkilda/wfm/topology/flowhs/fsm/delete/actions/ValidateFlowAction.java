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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ValidateFlowAction extends
        NbTrackableWithHistorySupportAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowDeleteContext context,
                                                    FlowDeleteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        dashboardLogger.onFlowDelete(flowId);

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getDeleteFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Flow delete feature is disabled");
        }

        Flow resultFlow = transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(flowId);
            if (flow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Flow %s is in progress now", flowId));
            }

            // Keep it, just in case we have to revert it.
            stateMachine.setOriginalFlowStatus(flow.getStatus());

            flow.setStatus(FlowStatus.IN_PROGRESS);
            return flow;
        });

        stateMachine.setDstSwitchId(resultFlow.getDestSwitchId());
        stateMachine.setSrcSwitchId(resultFlow.getSrcSwitchId());

        stateMachine.saveNewEventToHistory("Flow was validated successfully", FlowEventData.Event.DELETE);

        return Optional.of(buildResponseMessage(resultFlow, stateMachine.getCommandContext()));
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not delete flow";
    }
}
