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
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class PrepareFlowAction extends NbTrackableAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final FeatureTogglesRepository featureTogglesRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public PrepareFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowRerouteContext context,
                                                    FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Set<PathId> pathsToReroute =
                new HashSet<>(Optional.ofNullable(context.getPathsToReroute()).orElse(emptySet()));
        dashboardLogger.onFlowPathReroute(flowId, pathsToReroute, context.isForceReroute());

        // alter all affected flow's fields here
        Flow flow = persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow targetFlow = getFlow(flowId);

            targetFlow.setEncapsulationType(determineFlowEncapsulation(targetFlow));

            flowRepository.createOrUpdate(targetFlow);

            return targetFlow;
        });

        stateMachine.setRecreateIfSamePath(! stateMachine.getOriginalFlow().isActive() || context.isForceReroute());

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
            throw new FlowProcessingException(ErrorType.NOT_FOUND, format("Path(s) %s was not found in flow %s",
                            pathsToReroute.stream().map(PathId::toString).collect(Collectors.joining(",")),
                            flowId));
        }

        stateMachine.setReroutePrimary(reroutePrimary);
        stateMachine.setRerouteProtected(rerouteProtected);

        if (stateMachine.isRerouteProtected() && flow.isPinned()) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                    format("Flow %s is pinned, fail to reroute its protected paths", flowId));
        }

        String rerouteReason = context.getRerouteReason();
        stateMachine.saveNewEventToHistory("Flow was validated successfully", FlowEventData.Event.REROUTE,
                rerouteReason == null ? FlowEventData.Initiator.NB : FlowEventData.Initiator.AUTO,
                rerouteReason == null ? null : "Reason: " + rerouteReason);

        return Optional.empty();
    }

    private FlowEncapsulationType determineFlowEncapsulation(Flow originalFlow) {
        boolean forceDefault = featureTogglesRepository.find()
                .map(FeatureToggles::getFlowsRerouteUsingDefaultEncapType)
                .orElseGet(FeatureToggles.DEFAULTS::getFlowsRerouteUsingDefaultEncapType);

        if (forceDefault) {
            return kildaConfigurationRepository.get().getFlowEncapsulationType();
        } else {
            return originalFlow.getEncapsulationType();
        }
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }
}
