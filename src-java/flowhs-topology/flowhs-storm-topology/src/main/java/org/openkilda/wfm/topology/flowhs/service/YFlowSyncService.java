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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowSyncRequest;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.service.common.SyncServiceBase;
import org.openkilda.wfm.topology.flowhs.utils.HandlerResponseMapping;

import lombok.Getter;
import lombok.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class YFlowSyncService extends SyncServiceBase<YFlowSyncFsm, YFlowSyncFsm.Event>  {
    private final YFlowSyncFsm.Factory handlerFactory;

    private final HandlerResponseMapping<YFlowSyncFsm, FlowPathReference>
            pathResponseMapping = new HandlerResponseMapping<>();
    private final HandlerResponseMapping<YFlowSyncFsm, UUID> speakerResponseMapping = new HandlerResponseMapping<>();

    public YFlowSyncService(
            @NonNull FlowSyncCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesManager flowResourcesManager,
            RuleManager ruleManager, @NonNull FlowPathOperationConfig flowPathOperationConfig) {
        super(YFlowSyncFsm.EXECUTOR, carrier, persistenceManager);
        handlerFactory = new YFlowSyncFsm.Factory(
                persistenceManager, flowResourcesManager, ruleManager, flowPathOperationConfig);
    }

    @Override
    protected void handleRequest(String requestKey, String targetId, CommandContext commandContext) {
        log.debug("Handling Y-flow sync request with key {} and flow ID: {}", requestKey, targetId);
        super.handleRequest(requestKey, targetId, commandContext);
    }

    public void handleRequest(String requestKey, YFlowSyncRequest request, CommandContext commandContext) {
        handleRequest(requestKey, request.getYFlowId(), commandContext);
    }

    /**
     * Handle speaker response.
     */
    public boolean handleSpeakerResponse(SpeakerCommandResponse response) {
        Optional<YFlowSyncFsm> mapping = speakerResponseMapping.lookupAndRemove(response.getCommandId());
        if (mapping.isPresent()) {
            mapping.get().handleSpeakerResponse(response);
            return true;
        }
        return false;
    }

    @Override
    protected Optional<YFlowSyncFsm> lookupHandler(FlowPathReference reference) {
        return pathResponseMapping.lookupAndRemove(reference);
    }

    @Override
    protected YFlowSyncFsm newHandler(String flowId, CommandContext commandContext) {
        CarrierDecorator decoratedCarrier = new CarrierDecorator(
                carrier, substitute -> handlerFactory.newInstance(() -> substitute, flowId, commandContext));
        YFlowSyncFsm handler = decoratedCarrier.getHandler();
        handler.start(FlowSyncContext.builder().build());
        return handler;
    }

    @Override
    protected void onComplete(YFlowSyncFsm handler, String requestKey) {
        pathResponseMapping.remove(handler);
        speakerResponseMapping.remove(handler);
        super.onComplete(handler, requestKey);
    }

    private class CarrierDecorator implements FlowSyncCarrier {
        private final FlowSyncCarrier target;

        @Getter
        private final YFlowSyncFsm handler;

        public CarrierDecorator(FlowSyncCarrier target, Function<FlowSyncCarrier, YFlowSyncFsm> handlerBuilder) {
            this.target = target;
            this.handler = handlerBuilder.apply(this);
        }

        @Override
        public void sendSpeakerRequest(SpeakerRequest command) {
            speakerResponseMapping.add(command.getCommandId(), getHandler());
            target.sendSpeakerRequest(command);
        }

        @Override
        public void sendPeriodicPingNotification(String flowId, boolean enabled) {
            target.sendPeriodicPingNotification(flowId, enabled);
        }

        @Override
        public void launchFlowPathInstallation(
                @NonNull FlowPathRequest request, @NonNull FlowPathOperationConfig config,
                @NonNull CommandContext commandContext) throws DuplicateKeyException {
            pathResponseMapping.add(request.getReference(), getHandler());
            target.launchFlowPathInstallation(request, config, commandContext);
        }

        @Override
        public void cancelFlowPathOperation(PathId pathId) throws UnknownKeyException {
            target.cancelFlowPathOperation(pathId);
        }

        @Override
        public void sendHistoryUpdate(FlowHistoryHolder historyHolder) {
            target.sendHistoryUpdate(historyHolder);
        }

        @Override
        public void cancelTimeoutCallback(String key) {
            target.cancelTimeoutCallback(key);
        }

        @Override
        public void sendInactive() {
            target.sendInactive();
        }

        @Override
        public void sendNorthboundResponse(Message message) {
            target.sendNorthboundResponse(message);
        }
    }
}
