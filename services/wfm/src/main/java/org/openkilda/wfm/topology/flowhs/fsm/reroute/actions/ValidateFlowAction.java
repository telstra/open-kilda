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
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateFlowAction extends
        NbTrackableAction<FlowRerouteFsm, FlowRerouteFsm.State, FlowRerouteFsm.Event, FlowRerouteContext> {

    private final FlowRepository flowRepository;

    public ValidateFlowAction(PersistenceManager persistenceManager) {
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    @Override
    protected Optional<Message> perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                                        FlowRerouteFsm.Event event, FlowRerouteContext context,
                                        FlowRerouteFsm stateMachine) {
        String flowId = context.getFlowId();
        stateMachine.setFlowId(flowId);

        try {
            Optional<Flow> foundFlow = flowRepository.findById(flowId, FetchStrategy.NO_RELATIONS);
            if (!foundFlow.isPresent()) {
                throw new FlowProcessingException(ErrorType.NOT_FOUND,
                        getGenericErrorMessage(), format("Flow %s was not found", flowId));
            }

            Flow flow = foundFlow.get();
            if (flow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        getGenericErrorMessage(), format("Flow %s is in progress now", flowId));
            }

            stateMachine.setOriginalFlowStatus(flow.getStatus());
            stateMachine.setRecreateIfSamePath(!flow.isActive() || context.isForceReroute());

            Set<PathId> pathsToReroute =
                    new HashSet<>(Optional.ofNullable(context.getPathsToReroute()).orElse(emptySet()));
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

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, "Flow was validated successfully");

            return Optional.empty();

        } catch (FlowProcessingException e) {
            // This is a validation error.
            String errorMessage = format("%s: %s", e.getErrorMessage(), e.getErrorDescription());
            log.debug(errorMessage);

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, e.getErrorDescription());

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, e.getErrorType(), e.getErrorMessage(),
                    e.getErrorDescription()));
        }
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }
}
