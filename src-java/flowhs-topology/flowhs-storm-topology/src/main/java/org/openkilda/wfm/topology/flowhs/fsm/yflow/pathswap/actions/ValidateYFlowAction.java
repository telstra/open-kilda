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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
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
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ValidateYFlowAction extends
        NbTrackableWithHistorySupportAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {
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
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowPathSwapContext context,
                                                    YFlowPathSwapFsm stateMachine) {
        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getModifyYFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Y-flow path swap feature is disabled");
        }

        String yFlowId = stateMachine.getYFlowId();
        transactionManager.doInTransaction(() -> {
            YFlow result = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            if (result.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Y-flow %s is in progress now", yFlowId));
            }

            Collection<Flow> subFlows = result.getSubFlows().stream()
                    .map(YSubFlow::getFlow)
                    .collect(Collectors.toList());

            stateMachine.clearOldPrimaryPaths();
            stateMachine.clearNewPrimaryPaths();
            subFlows.forEach(subFlow -> {
                if (subFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                    String message = format("Sub-flow %s of y-flow %s is in progress now", subFlow.getFlowId(),
                            yFlowId);
                    throw new FlowProcessingException(ErrorType.REQUEST_INVALID, message);
                }

                if (!subFlow.isAllocateProtectedPath() || subFlow.getProtectedForwardPathId() == null
                        || subFlow.getProtectedForwardPath() == null
                        || subFlow.getProtectedReversePathId() == null
                        || subFlow.getProtectedReversePath() == null) {
                    throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                            format("Could not swap y-flow paths: sub-flow %s doesn't have a protected path",
                                    subFlow.getFlowId()));
                }
                if (subFlow.getProtectedForwardPath().getStatus() != FlowPathStatus.ACTIVE
                        || subFlow.getProtectedReversePath().getStatus() != FlowPathStatus.ACTIVE) {
                    throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                            format("Could not swap y-flow paths: the protected path of sub-flow %s is not in ACTIVE "
                                            + "state, but in %s/%s (forward/reverse) state",
                                    subFlow.getFlowId(), subFlow.getProtectedForwardPath().getStatus(),
                                    subFlow.getProtectedReversePath().getStatus()));
                }

                SwitchId sharedEndpoint = result.getSharedEndpoint().getSwitchId();
                if (!subFlow.getForwardPath().getSrcSwitchId().equals(sharedEndpoint)) {
                    throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                            format("Could not swap y-flow paths: the forward path source of sub-flow %s is different"
                                            + " from the y-flow shared endpoint %s",
                                    subFlow.getFlowId(), sharedEndpoint));
                }
                if (!subFlow.getProtectedForwardPath().getSrcSwitchId()
                        .equals(sharedEndpoint)) {
                    throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                            format("Could not swap y-flow paths: the protected forward path source of sub-flow %s "
                                            + " is different from the y-flow shared endpoint %s",
                                    subFlow.getFlowId(), sharedEndpoint));
                }

                stateMachine.addOldPrimaryPath(subFlow.getForwardPathId());
                stateMachine.addNewPrimaryPath(subFlow.getProtectedForwardPathId());
            });

            // Keep it, just in case we have to revert it.
            stateMachine.setOriginalYFlowStatus(result.getStatus());

            result.setStatus(FlowStatus.IN_PROGRESS);
        });

        dashboardLogger.onYFlowPathsSwap(yFlowId);

        stateMachine.saveNewEventToHistory("Y-flow was validated successfully", FlowEventData.Event.PATH_SWAP);

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap y-flow paths";
    }
}
