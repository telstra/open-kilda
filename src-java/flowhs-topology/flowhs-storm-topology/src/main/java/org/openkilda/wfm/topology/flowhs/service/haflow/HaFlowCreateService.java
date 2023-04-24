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
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import com.google.common.io.BaseEncoding;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HaFlowCreateService extends FlowProcessingService<HaFlowCreateFsm, Event, HaFlowCreateContext,
        FlowGenericCarrier, FlowProcessingFsmRegister<HaFlowCreateFsm>, FlowProcessingEventListener> {
    private static final String HA_FLOW_PREFIX = "haf-";
    private static final char SUB_FLOW_INITIAL_POSTFIX = 'a';

    private final HaFlowCreateFsm.Factory fsmFactory;
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final NoArgGenerator flowIdGenerator;

    public HaFlowCreateService(
            @NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager flowResourcesManager,
            @NonNull RuleManager ruleManager, int genericRetriesLimit, int pathAllocationRetriesLimit,
            int pathAllocationRetryDelay, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();

        Config fsmConfig = Config.builder()
                .haFlowCreationRetriesLimit(genericRetriesLimit)
                .pathAllocationRetriesLimit(pathAllocationRetriesLimit)
                .pathAllocationRetryDelay(pathAllocationRetryDelay)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new HaFlowCreateFsm.Factory(
                carrier, persistenceManager, flowResourcesManager, pathComputer, ruleManager, fsmConfig);
        flowIdGenerator = Generators.timeBasedGenerator();
    }

    /**
     * Handles request for ha-flow creation.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(
            @NonNull String key, @NonNull CommandContext commandContext, @NonNull HaFlowRequest request)
            throws DuplicateKeyException {
        log.debug("Handling ha-flow create request with key {} and flow ID: {}", key, request.getHaFlowId());

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        String haFlowId = request.getHaFlowId();
        if (haFlowId == null) {
            haFlowId = generateHaFlowId();
            request.setHaFlowId(haFlowId);
        } else if (fsmRegister.hasRegisteredFsmWithFlowId(haFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.ALREADY_EXISTS, "Could not create ha-flow",
                    format("HA-flow %s is already creating now", haFlowId), commandContext);
            return;
        }

        char postfix = SUB_FLOW_INITIAL_POSTFIX;
        for (HaSubFlowDto subFlow : request.getSubFlows()) {
            subFlow.setFlowId(String.format("%s-%c", haFlowId, postfix++));
        }

        if (request.getEncapsulationType() == null) {
            request.setEncapsulationType(kildaConfigurationRepository.getOrDefault().getFlowEncapsulationType());
        }
        if (request.getPathComputationStrategy() == null) {
            request.setPathComputationStrategy(
                    kildaConfigurationRepository.getOrDefault().getPathComputationStrategy());
        }

        HaFlowCreateFsm fsm = fsmFactory.newInstance(commandContext, haFlowId, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        HaFlowCreateContext context = HaFlowCreateContext.builder()
                .targetFlow(request)
                .build();
        fsm.start(context);
        fsmExecutor.fire(fsm, Event.NEXT, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from speaker.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerCommandResponse speakerCommandResponse) {
        log.debug("Received speaker command response {}", speakerCommandResponse);
        HaFlowCreateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        HaFlowCreateContext context = HaFlowCreateContext.builder()
                .speakerResponse(speakerCommandResponse)
                .build();

        fsmExecutor.fire(fsm, HaFlowCreateFsm.Event.RESPONSE_RECEIVED, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        HaFlowCreateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, HaFlowCreateFsm.Event.TIMEOUT, null);
        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(HaFlowCreateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }

    protected String generateHaFlowId() {
        byte[] uuidAsBytes = UUIDUtil.asByteArray(flowIdGenerator.generate());
        String uuidAsBase32 = BaseEncoding.base32().omitPadding().lowerCase().encode(uuidAsBytes);
        return HA_FLOW_PREFIX + uuidAsBase32;
    }
}
