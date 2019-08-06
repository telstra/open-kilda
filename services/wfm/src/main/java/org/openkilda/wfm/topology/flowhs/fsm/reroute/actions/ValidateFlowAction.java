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
import static java.util.Collections.emptySet;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowHistorySupportingCarrier;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateFlowAction extends
        NbTrackableAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final TransactionManager transactionManager;
    private final FlowRepository flowRepository;
    private final FlowEventRepository flowEventRepository;
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final FeatureTogglesRepository featureTogglesRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                                        FlowRerouteFsm.Event event, FlowRerouteContext context,
                                        FlowRerouteFsm stateMachine) {
        String flowId = context.getFlowId();
        stateMachine.setFlowId(flowId);

        String eventKey = stateMachine.getCommandContext().getCorrelationId();
        if (flowEventRepository.existsByTaskId(eventKey)) {
            String errorMessage = format("Attempt to reuse key %s, but there's a history record(s) for it.", eventKey);
            log.debug(errorMessage);

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.REQUEST_INVALID,
                    getGenericErrorMessage(), errorMessage));
        }

        Set<PathId> pathsToReroute =
                new HashSet<>(Optional.ofNullable(context.getPathsToReroute()).orElse(emptySet()));
        dashboardLogger.onFlowPathReroute(flowId, pathsToReroute, context.isForceReroute());

        try {
            Flow flow = transactionManager.doInTransaction(() -> {
                Flow foundFlow = flowRepository.findById(flowId, FetchStrategy.NO_RELATIONS)
                        .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                                getGenericErrorMessage(), format("Flow %s was not found", flowId)));
                if (foundFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                    throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                            getGenericErrorMessage(), format("Flow %s is in progress now", flowId));
                }

                stateMachine.setOriginalFlowStatus(foundFlow.getStatus());
                stateMachine.setOriginalEncapsulationType(foundFlow.getEncapsulationType());
                stateMachine.setRecreateIfSamePath(!foundFlow.isActive() || context.isForceReroute());

                flowRepository.updateStatus(foundFlow.getFlowId(), FlowStatus.IN_PROGRESS);
                return foundFlow;
            });

            featureTogglesRepository.find().ifPresent(featureToggles ->
                    Optional.ofNullable(featureToggles.getFlowsRerouteUsingDefaultEncapType()).ifPresent(toggle -> {
                        if (toggle) {
                            stateMachine.setNewEncapsulationType(
                                    kildaConfigurationRepository.get().getFlowEncapsulationType());
                        }
                    }));

            // check whether the primary paths should be rerouted
            // | operator is used intentionally, see validation below.
            boolean reroutePrimary = pathsToReroute.isEmpty() | pathsToReroute.remove(flow.getForwardPathId())
                    | pathsToReroute.remove(flow.getReversePathId());
            // check whether the protected paths should be rerouted
            // | operator is used intentionally, see validation below.
            boolean rerouteProtected = flow.isAllocateProtectedPath() && (pathsToReroute.isEmpty()
                    | pathsToReroute.remove(flow.getProtectedForwardPathId())
                    | pathsToReroute.remove(flow.getProtectedReversePathId()));

            if (!pathsToReroute.isEmpty()) {
                throw new FlowProcessingException(ErrorType.NOT_FOUND,
                        getGenericErrorMessage(), format("Path(s) %s was not found in flow %s",
                        pathsToReroute.stream().map(PathId::toString).collect(Collectors.joining(",")),
                        flowId));
            }

            stateMachine.setReroutePrimary(reroutePrimary);
            stateMachine.setRerouteProtected(rerouteProtected);

            if (stateMachine.isRerouteProtected() && flow.isPinned()) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID, getGenericErrorMessage(),
                        format("Flow %s is pinned, fail to reroute its protected paths", flowId));
            }

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, "Flow was validated successfully");

            return Optional.empty();

        } catch (FlowProcessingException e) {
            // This is a validation error.
            String errorMessage = format("%s: %s", e.getErrorMessage(), e.getErrorDescription());
            dashboardLogger.onFailedFlowReroute(flowId, errorMessage);

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, e.getErrorDescription());

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, e.getErrorType(), e.getErrorMessage(),
                    e.getErrorDescription()));
        }
    }

    @Override
    protected void saveHistory(FlowRerouteFsm stateMachine, FlowHistorySupportingCarrier carrier,
                               String flowId, String action) {
        Instant timestamp = Instant.now();
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(Instant.now())
                        .flowId(flowId)
                        .build())
                .flowEventData(FlowEventData.builder()
                        .flowId(flowId)
                        .event(FlowEventData.Event.REROUTE)
                        .time(timestamp)
                        .build())
                .build();
        carrier.sendHistoryUpdate(historyHolder);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }
}
