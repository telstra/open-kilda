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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions;

import static java.lang.String.format;
import static java.util.Collections.emptySet;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.error.FlowInProgressError;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathSegment;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateYFlowAction extends
        NbTrackableWithHistorySupportAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final YFlowRepository yFlowRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateYFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowRerouteContext context,
                                                    YFlowRerouteFsm stateMachine) {

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getModifyYFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Y-flow reroute feature is disabled");
        }

        YFlowRerouteRequest request = context.getRerouteRequest();
        String yFlowId = request.getYFlowId();
        Set<IslEndpoint> affectedIsls =
                new HashSet<>(Optional.ofNullable(request.getAffectedIsl()).orElse(emptySet()));

        dashboardLogger.onYFlowReroute(yFlowId, affectedIsls, request.isForce());

        stateMachine.setAffectedIsls(affectedIsls);
        stateMachine.setRerouteReason(request.getReason());
        stateMachine.setForceReroute(request.isForce());
        stateMachine.setIgnoreBandwidth(request.isIgnoreBandwidth());

        YFlow yFlow = transactionManager.doInTransaction(() -> {
            YFlow result = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            if (result.getStatus() == FlowStatus.IN_PROGRESS) {
                String message = format("Y-flow %s is in progress now", yFlowId);
                stateMachine.setRerouteError(new FlowInProgressError(message));
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID, message);
            }

            Collection<Flow> subFlows = result.getSubFlows().stream()
                    .map(YSubFlow::getFlow)
                    .collect(Collectors.toList());

            subFlows.forEach(subFlow -> {
                if (subFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                    String message = format("Sub-flow %s of y-flow %s is in progress now", subFlow.getFlowId(),
                            yFlowId);
                    stateMachine.setRerouteError(new FlowInProgressError(message));
                    throw new FlowProcessingException(ErrorType.REQUEST_INVALID, message);
                }
            });

            Flow mainAffinitySubFlow = subFlows.stream()
                    .filter(flow -> flow.getFlowId().equals(flow.getAffinityGroupId()))
                    .findFirst()
                    .orElseGet(() ->
                            subFlows.stream()
                                    .findFirst()
                                    .orElseThrow(() -> new FlowProcessingException(ErrorType.DATA_INVALID,
                                            format("Main affinity sub-flow of the y-flow %s not found", yFlowId))));

            stateMachine.setMainAffinityFlowId(mainAffinitySubFlow.getFlowId());

            boolean mainAffinitySubFlowIsAffected = isFlowAffected(mainAffinitySubFlow, affectedIsls);
            Set<String> affectedFlowIds = subFlows.stream()
                    .filter(flow -> stateMachine.isForceReroute() || mainAffinitySubFlowIsAffected
                            || isFlowAffected(flow, affectedIsls))
                    .map(Flow::getFlowId)
                    .collect(Collectors.toSet());
            stateMachine.setTargetSubFlowIds(affectedFlowIds);

            // Keep it, just in case we have to revert it.
            stateMachine.setOriginalYFlowStatus(result.getStatus());

            if (affectedFlowIds.isEmpty()) {
                // No sub-flows to be rerouted, so skip the whole y-flow reroute.
                result.recalculateStatus();
                dashboardLogger.onYFlowStatusUpdate(yFlowId, result.getStatus());
                stateMachine.saveActionToHistory(format("The y-flow status was set to %s", result.getStatus()));
                return null;
            } else {
                result.setStatus(FlowStatus.IN_PROGRESS);
                return result;
            }
        });

        if (yFlow != null) {
            stateMachine.saveNewEventToHistory("Y-flow was validated successfully", FlowEventData.Event.REROUTE);
        } else {
            stateMachine.saveNewEventToHistory("Y-flow was validated, no sub-flows to be rerouted",
                    FlowEventData.Event.REROUTE);
            stateMachine.fire(Event.YFLOW_REROUTE_SKIPPED);
        }

        return Optional.empty();
    }

    private boolean isFlowAffected(Flow flow, Set<IslEndpoint> affectedIsls) {
        if (affectedIsls.isEmpty()) {
            return true;
        }
        return isFlowPathAffected(flow.getForwardPath(), affectedIsls)
                || isFlowPathAffected(flow.getReversePath(), affectedIsls)
                || isFlowPathAffected(flow.getProtectedForwardPath(), affectedIsls)
                || isFlowPathAffected(flow.getProtectedReversePath(), affectedIsls);
    }

    private boolean isFlowPathAffected(FlowPath path, Set<IslEndpoint> affectedIsls) {
        if (path == null) {
            return false;
        }

        boolean isAffected = false;
        for (PathSegment segment : path.getSegments()) {
            isAffected = affectedIsls.contains(getSegmentSourceEndpoint(segment));
            if (!isAffected) {
                isAffected = affectedIsls.contains(getSegmentDestEndpoint(segment));
            }

            if (isAffected) {
                break;
            }
        }

        return isAffected;
    }

    private IslEndpoint getSegmentSourceEndpoint(PathSegment segment) {
        return new IslEndpoint(segment.getSrcSwitchId(), segment.getSrcPort());
    }

    private IslEndpoint getSegmentDestEndpoint(PathSegment segment) {
        return new IslEndpoint(segment.getDestSwitchId(), segment.getDestPort());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute y-flow";
    }
}
