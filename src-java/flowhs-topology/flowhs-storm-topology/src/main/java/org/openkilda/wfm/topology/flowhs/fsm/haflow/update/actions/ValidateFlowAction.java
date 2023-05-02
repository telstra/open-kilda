/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaFlowPathIds;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaPathIdsPairBuilder;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.HaFlowValidator;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateFlowAction extends
        NbTrackableWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final HaFlowValidator haFlowValidator;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        haFlowValidator = new HaFlowValidator(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();
        HaFlowRequest targetHaFlow = context.getTargetFlow();
        dashboardLogger.onHaFlowUpdate(haFlowId);

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getModifyHaFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "HA-Flow update feature is disabled");
        }

        stateMachine.setTargetHaFlow(targetHaFlow);

        try {
            haFlowValidator.validate(targetHaFlow);
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage(), e);
        } catch (UnavailableFlowEndpointException e) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID, e.getMessage(), e);
        }
        Set<String> targetSubFlowIds = targetHaFlow.getSubFlows().stream()
                .map(HaSubFlowDto::getFlowId).collect(Collectors.toSet());
        transactionManager.doInTransaction(() -> {
            HaFlow foundHaFlow = getHaFlow(haFlowId);
            if (foundHaFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("HA-flow %s is in progress now", haFlowId));
            }
            Set<String> foundSubFlowIds = new HashSet<>();
            for (HaSubFlow foundSubFlow : foundHaFlow.getHaSubFlows()) {
                if (foundSubFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                    throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                            format("HA-sub flow %s is in progress now", foundSubFlow.getHaSubFlowId()));
                }
                foundSubFlowIds.add(foundSubFlow.getHaSubFlowId());
            }

            if (!foundSubFlowIds.equals(targetSubFlowIds)) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Invalid sub flow IDs: %s. Valid sub flows IDs are: %s",
                                targetSubFlowIds, foundSubFlowIds));
            }

            HaFlow originalHaFlow = new HaFlow(foundHaFlow); // detaching ha-flow from DB
            stateMachine.setOriginalHaFlow(originalHaFlow);

            foundHaFlow.setStatus(FlowStatus.IN_PROGRESS);
            return foundHaFlow;
        });

        saveOldPathIds(stateMachine);
        stateMachine.saveNewEventToHistory("Ha-flow was validated successfully", FlowEventData.Event.UPDATE);

        return Optional.empty();
    }

    private void saveOldPathIds(HaFlowUpdateFsm stateMachine) {
        HaFlow haFlow = stateMachine.getOriginalHaFlow();
        stateMachine.setOldPrimaryPathIds(buildPathIds(haFlow.getForwardPath(), haFlow.getReversePath()));
        stateMachine.setOldProtectedPathIds(buildPathIds(
                haFlow.getProtectedForwardPath(), haFlow.getProtectedReversePath()));
    }

    private HaPathIdsPair buildPathIds(HaFlowPath forward, HaFlowPath reverse) {
        HaPathIdsPairBuilder haPathIdsPairBuilder = HaPathIdsPair.builder();
        if (forward != null) {
            haPathIdsPairBuilder.forward(buildPathIds(forward));
        }
        if (reverse != null) {
            haPathIdsPairBuilder.reverse(buildPathIds(reverse));
        }
        if (forward != null || reverse != null) {
            return haPathIdsPairBuilder.build();
        }
        return null;
    }

    private static HaFlowPathIds buildPathIds(HaFlowPath haFlowPath) {
        return HaFlowPathIds.builder()
                .haPathId(haFlowPath.getHaPathId())
                .subPathIds(buildSubPathIdMap(haFlowPath.getSubPaths()))
                .build();
    }

    private static Map<String, PathId> buildSubPathIdMap(Collection<FlowPath> subPaths) {
        Map<String, PathId> result = new HashMap<>();
        if (subPaths != null) {
            for (FlowPath subPath : subPaths) {
                result.put(subPath.getHaSubFlowId(), subPath.getPathId());
            }
        }
        return result;
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Couldn't update HA-flow";
    }

    @Override
    protected void handleError(HaFlowUpdateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed validation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
