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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static org.openkilda.wfm.share.history.model.HaFlowEventData.Initiator.AUTO;
import static org.openkilda.wfm.share.history.model.HaFlowEventData.Initiator.NB;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.error.FlowInProgressError;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathSegment;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateHaFlowAction extends
        NbTrackableWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateHaFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @TimedExecution("fsm.validate_ha_flow")
    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, HaFlowRerouteContext context,
                                                    HaFlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Set<IslEndpoint> affectedIsl =
                new HashSet<>(Optional.ofNullable(context.getAffectedIsl()).orElse(emptySet()));
        dashboardLogger.onHaFlowReroute(flowId, affectedIsl);

        String rerouteReason = context.getRerouteReason();

        FlowHistoryService.using(stateMachine.getCarrier()).saveNewHaFlowEvent(HaFlowEventData.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .event(HaFlowEventData.Event.REROUTE)
                .haFlowId(stateMachine.getHaFlowId())
                .action("Started HA-flow validation")
                .initiator(rerouteReason == null ? NB : AUTO)
                .details(rerouteReason == null ? null : "Reason: " + rerouteReason)
                .build());

        stateMachine.setRerouteReason(rerouteReason);

        HaFlow haFlow = transactionManager.doInTransaction(() -> {
            HaFlow foundHaFlow = getHaFlow(flowId);
            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow validation started.")
                    .withDescription("Saving a dump before.")
                    .withHaFlowDumpBefore(foundHaFlow));

            if (foundHaFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                String message = format("HA-flow %s is in progress now", flowId);
                stateMachine.setRerouteError(new FlowInProgressError(message));
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID, message);
            }

            if (!foundHaFlow.getSharedSwitch().isActive()) {
                String message = format("HA-flow's %s src switch %s is not active",
                        flowId, foundHaFlow.getSharedSwitchId());
                stateMachine.setRerouteError(new RerouteError(message));
                throw new FlowProcessingException(ErrorType.UNPROCESSABLE_REQUEST, message);
            }
            for (HaSubFlow haSubFlow : foundHaFlow.getHaSubFlows()) {
                if (!haSubFlow.getEndpointSwitch().isActive()) {
                    String message = format("Ha-flow's %s endpoint switch %s is not active",
                            flowId, haSubFlow.getEndpointSwitchId());
                    stateMachine.setRerouteError(new RerouteError(message));
                    throw new FlowProcessingException(ErrorType.UNPROCESSABLE_REQUEST, message);
                }
            }

            stateMachine.setOriginalFlowStatus(foundHaFlow.getStatus());
            stateMachine.setRecreateIfSamePath(foundHaFlow.getStatus() != FlowStatus.UP);
            stateMachine.setOriginalHaFlow(new HaFlow(foundHaFlow)); // detach HA-flow
            stateMachine.setPeriodicPingsEnabled(foundHaFlow.isPeriodicPings());

            foundHaFlow.setStatus(FlowStatus.IN_PROGRESS);
            foundHaFlow.getHaSubFlows().forEach(subFlow -> subFlow.setStatus(FlowStatus.IN_PROGRESS));
            return foundHaFlow;
        });

        if (featureTogglesRepository.getOrDefault().getFlowsRerouteUsingDefaultEncapType()) {
            stateMachine.setNewEncapsulationType(
                    kildaConfigurationRepository.getOrDefault().getFlowEncapsulationType());
        }

        boolean reroutePrimary;
        boolean rerouteProtected;
        if (affectedIsl.isEmpty()) {
            // don't know affected ISLs
            reroutePrimary = true;
            rerouteProtected = true;
        } else {
            reroutePrimary = isPathAffected(haFlow.getForwardPath(), affectedIsl)
                    || isPathAffected(haFlow.getReversePath(), affectedIsl);
            // Reroute the protected if the primary is affected to properly handle the case of overlapped paths.
            rerouteProtected = reroutePrimary || isPathAffected(haFlow.getProtectedForwardPath(), affectedIsl)
                    || isPathAffected(haFlow.getProtectedReversePath(), affectedIsl);
        }
        // check protected path presence
        rerouteProtected &= haFlow.isAllocateProtectedPath();

        if (!reroutePrimary && !rerouteProtected) {
            throw new FlowProcessingException(ErrorType.NOT_FOUND, format(
                    "No paths of the HA-flow %s are affected by failure on %s", flowId,
                    affectedIsl.stream()
                            .map(IslEndpoint::toString)
                            .collect(Collectors.joining(","))));
        }

        if (reroutePrimary) {
            log.info("Reroute for the HA-flow {} will affect primary paths: {} / {}",
                    flowId, haFlow.getForwardPathId(), haFlow.getReversePathId());
        }
        if (rerouteProtected) {
            log.info("Reroute for the HA-flow {} will affect protected paths: {} / {}",
                    flowId, haFlow.getProtectedForwardPathId(), haFlow.getProtectedReversePathId());
        }

        stateMachine.setReroutePrimary(reroutePrimary);
        stateMachine.setRerouteProtected(rerouteProtected);
        stateMachine.setEffectivelyDown(context.isEffectivelyDown());

        if (stateMachine.isRerouteProtected() && haFlow.isPinned()) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                    format("HA-flow %s is pinned, fail to reroute its protected paths", flowId));
        }

        stateMachine.setAffectedIsls(context.getAffectedIsl());
        stateMachine.setIgnoreBandwidth(context.isIgnoreBandwidth());
        stateMachine.saveOldPathIds(stateMachine.getOriginalHaFlow());

        FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .of(stateMachine.getCommandContext().getCorrelationId())
                .withAction("HA-flow has been validated successfully")
                .withHaFlowId(stateMachine.getHaFlowId()));

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute HA-flow";
    }

    private boolean isPathAffected(HaFlowPath haFlowPath, Set<IslEndpoint> affectedIsl) {
        if (haFlowPath == null || haFlowPath.getSubPaths() == null) {
            return false;
        }

        for (FlowPath subPath : haFlowPath.getSubPaths()) {
            if (subPath == null) {
                continue;
            }

            for (PathSegment segment : subPath.getSegments()) {
                if (affectedIsl.contains(IslEndpoint.buildSourceEndpoint(segment))
                        || affectedIsl.contains(IslEndpoint.buildDestinationEndpoint(segment))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void handleError(HaFlowRerouteFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed validation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
