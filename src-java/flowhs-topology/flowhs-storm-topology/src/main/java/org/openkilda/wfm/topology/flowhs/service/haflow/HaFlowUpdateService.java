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

package org.openkilda.wfm.topology.flowhs.service.haflow;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest.Type;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.command.haflow.HaSubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;
import org.openkilda.wfm.topology.flowhs.validation.HaFlowValidator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class HaFlowUpdateService extends FlowProcessingService<HaFlowUpdateFsm, Event, HaFlowUpdateContext,
        FlowGenericCarrier, FlowProcessingFsmRegister<HaFlowUpdateFsm>, FlowProcessingEventListener> {
    private static final Set<PathComputationStrategy> LATENCY_BASED_STRATEGIES =
            Sets.newHashSet(PathComputationStrategy.LATENCY, PathComputationStrategy.MAX_LATENCY);

    private final HaFlowUpdateFsm.Factory fsmFactory;
    private final FlowOperationsDashboardLogger flowDashboardLogger = new FlowOperationsDashboardLogger(log);
    private final HaFlowValidator haFlowValidator;
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final TransactionManager transactionManager;

    public HaFlowUpdateService(
            @NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager flowResourcesManager,
            @NonNull RuleManager ruleManager, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
            int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);

        Config fsmConfig = Config.builder()
                .pathAllocationRetriesLimit(pathAllocationRetriesLimit)
                .pathAllocationRetryDelay(pathAllocationRetryDelay)
                .resourceAllocationRetriesLimit(resourceAllocationRetriesLimit)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new HaFlowUpdateFsm.Factory(carrier, fsmConfig, persistenceManager, ruleManager, pathComputer,
                flowResourcesManager);
        haFlowValidator = new HaFlowValidator(persistenceManager);
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        transactionManager = persistenceManager.getTransactionManager();
    }

    /**
     * Handles request for ha-flow update.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleUpdateRequest(
            @NonNull String key, @NonNull CommandContext commandContext, @NonNull HaFlowRequest request)
            throws DuplicateKeyException {

        String haFlowId = request.getHaFlowId();
        log.debug("Handling ha-flow update request with key {} and flow ID: {}", key, haFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        if (fsmRegister.hasRegisteredFsmWithFlowId(haFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Couldn't update HA-flow",
                    format("Ha-flow %s is updating now", haFlowId), commandContext);
            log.error("Attempt to create a FSM with key {}, while there's another active FSM for the same haFlowId {}.",
                    key, haFlowId);
            cancelProcessing(key);
            return;
        }

        HaFlowUpdateFsm fsm = fsmFactory.newInstance(haFlowId, commandContext, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        HaFlowUpdateContext context = HaFlowUpdateContext.builder().targetFlow(request).build();
        fsmExecutor.fire(fsm, Event.NEXT, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles request for ha-flow update.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handlePartialUpdateRequest(
            @NonNull String key, @NonNull CommandContext commandContext, @NonNull HaFlowPartialUpdateRequest request)
            throws FlowProcessingException, DuplicateKeyException {
        if (!featureTogglesRepository.getOrDefault().getModifyHaFlowEnabled()) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "HA-Flow update feature is disabled");
        }
        String haFlowId = request.getHaFlowId();
        flowDashboardLogger.onHaFlowPatchUpdate(haFlowId);
        UpdateHaFlowResult updateHaFlowResult = updateHaFlow(request, haFlowId);

        if (updateHaFlowResult.isFullUpdateRequired) {
            HaFlowRequest updateRequest = HaFlowMapper.INSTANCE.toHaFlowRequest(
                    updateHaFlowResult.originalHaFlow, request.getDiverseFlowId(), Type.UPDATE);
            updateChangedFields(updateRequest, request);
            handleUpdateRequest(key, commandContext, updateRequest);
        } else {
            if (isFieldUpdated(request.getPeriodicPings(),
                    updateHaFlowResult.originalHaFlow.isPeriodicPings())) {
                carrier.sendPeriodicPingNotification(haFlowId, request.getPeriodicPings());
            }
            carrier.sendNorthboundResponse(buildResponseMessage(
                    updateHaFlowResult.partiallyUpdatedHaFlow, commandContext));
        }
    }

    private UpdateHaFlowResult updateHaFlow(HaFlowPartialUpdateRequest request, String haFlowId)
            throws FlowProcessingException {
        return transactionManager.doInTransaction(() -> {
            HaFlow haFlow = haFlowRepository.findById(haFlowId)
                    .orElseThrow(() -> new FlowProcessingException(
                            ErrorType.NOT_FOUND, format("HA-flow %s not found", haFlowId)));

            Set<String> existingSubFlowIds = haFlow.getHaSubFlows().stream()
                    .map(HaSubFlow::getHaSubFlowId).collect(Collectors.toSet());

            if (request.getSubFlows() != null) {
                validateSubFlowIds(request, haFlowId, existingSubFlowIds);
            }

            boolean isFullUpdateRequired = isFullUpdateRequired(request, haFlow);
            UpdateHaFlowResult.UpdateHaFlowResultBuilder result = UpdateHaFlowResult.builder()
                    .isFullUpdateRequired(isFullUpdateRequired)
                    .originalHaFlow(new HaFlow(haFlow));

            if (!isFullUpdateRequired) {
                log.debug("Updating the HA-flow {} partially", haFlowId);
                updateHaFlowPartially(haFlow, request);
                result.partiallyUpdatedHaFlow(new HaFlow(haFlow));
            }
            return result.build();
        });
    }

    @VisibleForTesting
    void updateChangedFields(HaFlowRequest target, HaFlowPartialUpdateRequest source) {
        if (source.getSharedEndpoint() != null) {
            target.setSharedEndpoint(buildUpdatedEndpoint(target.getSharedEndpoint(), source.getSharedEndpoint()));
        }
        Optional.ofNullable(source.getMaximumBandwidth()).ifPresent(target::setMaximumBandwidth);
        Optional.ofNullable(source.getPathComputationStrategy()).ifPresent(target::setPathComputationStrategy);
        Optional.ofNullable(source.getEncapsulationType()).ifPresent(target::setEncapsulationType);
        Optional.ofNullable(source.getMaxLatency()).ifPresent(target::setMaxLatency);
        Optional.ofNullable(source.getMaxLatencyTier2()).ifPresent(target::setMaxLatencyTier2);
        Optional.ofNullable(source.getIgnoreBandwidth()).ifPresent(target::setIgnoreBandwidth);
        Optional.ofNullable(source.getPeriodicPings()).ifPresent(target::setPeriodicPings);
        Optional.ofNullable(source.getPinned()).ifPresent(target::setPinned);
        Optional.ofNullable(source.getPriority()).ifPresent(target::setPriority);
        Optional.ofNullable(source.getStrictBandwidth()).ifPresent(target::setStrictBandwidth);
        Optional.ofNullable(source.getDescription()).ifPresent(target::setDescription);
        Optional.ofNullable(source.getAllocateProtectedPath()).ifPresent(target::setAllocateProtectedPath);
        Optional.ofNullable(source.getDiverseFlowId()).ifPresent(target::setDiverseFlowId);
        if (source.getSubFlows() != null) {
            updateSubFlows(target, source.getSubFlows());
        }
    }

    private void updateSubFlows(HaFlowRequest target, List<HaSubFlowPartialUpdateDto> sourceSubFlows) {
        for (HaSubFlowPartialUpdateDto sourceSubFlow : sourceSubFlows) {
            if (sourceSubFlow == null) {
                continue;
            }
            for (HaSubFlowDto targetSubFlow : target.getSubFlows()) {
                if (sourceSubFlow.getFlowId().equals(targetSubFlow.getFlowId())) {
                    Optional.ofNullable(sourceSubFlow.getDescription()).ifPresent(targetSubFlow::setDescription);
                    if (sourceSubFlow.getEndpoint() != null) {
                        targetSubFlow.setEndpoint(buildUpdatedEndpoint(
                                targetSubFlow.getEndpoint(), sourceSubFlow.getEndpoint()));
                    }
                    break;
                }
            }
        }
    }

    private FlowEndpoint buildUpdatedEndpoint(FlowEndpoint target, FlowPartialUpdateEndpoint source) {
        SwitchId switchId = Optional.ofNullable(source.getSwitchId()).orElse(target.getSwitchId());
        Integer port = Optional.ofNullable(source.getPortNumber()).orElse(target.getPortNumber());
        Integer vlan = Optional.ofNullable(source.getVlanId()).orElse(target.getOuterVlanId());
        Integer innerVlan = Optional.ofNullable(source.getInnerVlanId()).orElse(target.getInnerVlanId());
        return new FlowEndpoint(switchId, port, vlan, innerVlan);
    }

    @VisibleForTesting
    void updateHaFlowPartially(HaFlow target, HaFlowPartialUpdateRequest source) {
        Optional.ofNullable(source.getMaxLatency()).ifPresent(target::setMaxLatency);
        Optional.ofNullable(source.getMaxLatencyTier2()).ifPresent(target::setMaxLatencyTier2);
        Optional.ofNullable(source.getPeriodicPings()).ifPresent(target::setPeriodicPings);
        Optional.ofNullable(source.getPinned()).ifPresent(target::setPinned);
        Optional.ofNullable(source.getPriority()).ifPresent(target::setPriority);
        Optional.ofNullable(source.getStrictBandwidth()).ifPresent(target::setStrictBandwidth);
        Optional.ofNullable(source.getDescription()).ifPresent(target::setDescription);
        if (source.getSubFlows() != null) {
            for (HaSubFlowPartialUpdateDto subFlow : source.getSubFlows()) {
                if (subFlow != null) {
                    Optional.ofNullable(subFlow.getDescription())
                            .ifPresent(description -> target.getHaSubFlowOrThrowException(
                                    subFlow.getFlowId()).setDescription(description));
                }
            }
        }
    }

    private void validateSubFlowIds(HaFlowPartialUpdateRequest request, String haFlowId, Set<String> existingSubFlowIds)
            throws FlowProcessingException {
        try {
            haFlowValidator.validateHaSubFlowIds(haFlowId, existingSubFlowIds, request.getSubFlows());
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage());
        }
    }

    @VisibleForTesting
    boolean isFullUpdateRequired(HaFlowPartialUpdateRequest request, HaFlow haFlow) {
        return isUpdateRequiredByPathComputationStrategy(request, haFlow)
                || isUpdateRequiredBySharedEndpoint(request, haFlow)
                || isUpdateRequiredBySubFlowEndpoints(request, haFlow)
                || isFieldUpdated(request.getMaximumBandwidth(), haFlow.getMaximumBandwidth())
                || isFieldUpdated(request.getAllocateProtectedPath(), haFlow.isAllocateProtectedPath())
                || isFieldUpdated(request.getIgnoreBandwidth(), haFlow.isIgnoreBandwidth())
                || isFieldUpdated(request.getEncapsulationType(), haFlow.getEncapsulationType())
                || isUpdateRequiredByDiverseFlowIdField(request, haFlow);
    }

    private static boolean isUpdateRequiredByPathComputationStrategy(
            HaFlowPartialUpdateRequest request, HaFlow haFlow) {
        PathComputationStrategy strategy = Optional.ofNullable(request.getPathComputationStrategy())
                .orElse(haFlow.getPathComputationStrategy());
        boolean strategyIsLatencyBased = LATENCY_BASED_STRATEGIES.contains(strategy);
        boolean changedMaxLatency = isFieldUpdated(request.getMaxLatency(), haFlow.getMaxLatency());
        boolean changedMaxLatencyTier2 = isFieldUpdated(request.getMaxLatencyTier2(), haFlow.getMaxLatencyTier2());
        boolean changedStrategy = isFieldUpdated(
                request.getPathComputationStrategy(), haFlow.getPathComputationStrategy());

        return changedStrategy || (strategyIsLatencyBased && (changedMaxLatency || changedMaxLatencyTier2));
    }

    private static boolean isUpdateRequiredBySharedEndpoint(HaFlowPartialUpdateRequest request, HaFlow haFlow) {
        if (request.getSharedEndpoint() == null) {
            return false;
        }
        FlowPartialUpdateEndpoint requestEndpoint = request.getSharedEndpoint();
        FlowEndpoint haFlowEndpoint = haFlow.getSharedEndpoint();
        return isUpdateRequiredByEndpoint(requestEndpoint, haFlowEndpoint);
    }

    private static boolean isUpdateRequiredBySubFlowEndpoints(HaFlowPartialUpdateRequest request, HaFlow haFlow) {
        if (request.getSubFlows() == null) {
            return false;
        }
        for (HaSubFlowPartialUpdateDto requestedSubFlow : request.getSubFlows()) {
            if (requestedSubFlow.getEndpoint() == null) {
                continue;
            }
            HaSubFlow haSubFlow = haFlow.getHaSubFlowOrThrowException(requestedSubFlow.getFlowId());
            FlowEndpoint haFlowEndpoint = new FlowEndpoint(haSubFlow.getEndpointSwitchId(), haSubFlow.getEndpointPort(),
                    haSubFlow.getEndpointVlan(), haSubFlow.getEndpointInnerVlan());
            if (isUpdateRequiredByEndpoint(requestedSubFlow.getEndpoint(), haFlowEndpoint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUpdateRequiredByEndpoint(
            FlowPartialUpdateEndpoint requestEndpoint, FlowEndpoint haFlowEndpoint) {
        return isFieldUpdated(requestEndpoint.getSwitchId(), haFlowEndpoint.getSwitchId())
                || isFieldUpdated(requestEndpoint.getPortNumber(), haFlowEndpoint.getPortNumber())
                || isFieldUpdated(requestEndpoint.getVlanId(), haFlowEndpoint.getOuterVlanId())
                || isFieldUpdated(requestEndpoint.getInnerVlanId(), haFlowEndpoint.getInnerVlanId());
    }

    static boolean isFieldUpdated(Object requestField, Object haFlowField) {
        return requestField != null && !requestField.equals(haFlowField);
    }

    private boolean isUpdateRequiredByDiverseFlowIdField(HaFlowPartialUpdateRequest request, HaFlow haflow) {
        if (request.getDiverseFlowId() == null) {
            return false;
        }
        Optional<String> groupId = getDiverseGroupId(request.getDiverseFlowId());
        return !groupId.isPresent()
                || !haFlowRepository.findHaFlowIdsByDiverseGroupId(groupId.get()).contains(haflow.getHaFlowId());
    }

    private Optional<String> getDiverseGroupId(String diverseFlowId) {
        if (yFlowRepository.exists(diverseFlowId)) {
            return yFlowRepository.getDiverseYFlowGroupId(diverseFlowId);
        } else if (yFlowRepository.isSubFlow(diverseFlowId)) {
            return flowRepository.findById(diverseFlowId)
                    .map(Flow::getYFlowId)
                    .flatMap(yFlowRepository::getDiverseYFlowGroupId);
        } else if (flowRepository.exists(diverseFlowId)) {
            return flowRepository.getDiverseFlowGroupId(diverseFlowId);
        } else {
            return haFlowRepository.getDiverseHaFlowGroupId(diverseFlowId);
        }
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerCommandResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        HaFlowUpdateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        HaFlowUpdateContext context = HaFlowUpdateContext.builder()
                .speakerResponse(flowResponse)
                .build();

        fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(@NonNull String key) {
        log.debug("Handling timeout for {}", key);
        HaFlowUpdateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT);
        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(HaFlowUpdateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }

    protected Message buildResponseMessage(HaFlow haFlow, CommandContext commandContext) {
        HaFlowResponse response = new HaFlowResponse(
                HaFlowMapper.INSTANCE.toHaFlowDto(haFlow, flowRepository, haFlowRepository));
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    @Value
    @Builder
    private static class UpdateHaFlowResult {
        HaFlow originalHaFlow;
        HaFlow partiallyUpdatedHaFlow;
        boolean isFullUpdateRequired;
    }
}
