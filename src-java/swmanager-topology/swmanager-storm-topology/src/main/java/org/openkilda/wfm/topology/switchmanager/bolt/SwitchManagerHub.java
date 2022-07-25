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

package org.openkilda.wfm.topology.switchmanager.bolt;

import static java.lang.String.format;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.ModifySpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.swmanager.request.CreateLagPortRequest;
import org.openkilda.messaging.swmanager.request.DeleteLagPortRequest;
import org.openkilda.messaging.swmanager.request.UpdateLagPortRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.switchmanager.StreamType;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerTopologyConfig;
import org.openkilda.wfm.topology.switchmanager.error.SwitchManagerException;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.CreateLagPortService;
import org.openkilda.wfm.topology.switchmanager.service.DeleteLagPortService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrierCookieDecorator;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerHubService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchRuleService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchSyncService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchValidateService;
import org.openkilda.wfm.topology.switchmanager.service.UpdateLagPortService;
import org.openkilda.wfm.topology.switchmanager.service.configs.LagPortOperationConfig;
import org.openkilda.wfm.topology.switchmanager.service.configs.SwitchSyncConfig;
import org.openkilda.wfm.topology.switchmanager.service.impl.ValidationServiceImpl;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public class SwitchManagerHub extends HubBolt implements SwitchManagerCarrier {
    public static final String ID = "switch.manager.hub";

    public static final String INCOME_STREAM = "switch.manage.command";

    public static final String FIELD_ID_COOKIE = SpeakerWorkerBolt.FIELD_ID_COOKIE;

    public static final Fields WORKER_STREAM_FIELDS =
            new Fields(
                    MessageKafkaTranslator.FIELD_ID_KEY, MessageKafkaTranslator.FIELD_ID_PAYLOAD, FIELD_ID_COOKIE,
                    FIELD_ID_CONTEXT);

    public static final Fields HEAVY_OPERATION_STREAM_FIELDS =
            new Fields(MessageKafkaTranslator.FIELD_ID_KEY, MessageKafkaTranslator.FIELD_ID_PAYLOAD, FIELD_ID_COOKIE,
                    FIELD_ID_CONTEXT);

    public static final String NORTHBOUND_STREAM_ID = StreamType.TO_NORTHBOUND.toString();
    public static final Fields NORTHBOUND_STREAM_FIELDS = new Fields(
            MessageKafkaTranslator.FIELD_ID_KEY, MessageKafkaTranslator.FIELD_ID_PAYLOAD);

    public static final String ZOOKEEPER_STREAM_ID = ZkStreams.ZK.toString();
    public static final Fields ZOOKEEPER_STREAM_FIELDS = new Fields(
            ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT);

    private final SwitchManagerTopologyConfig topologyConfig;
    private final RuleManagerConfig ruleManagerConfig;

    private transient SwitchValidateService validateService;
    private transient SwitchSyncService syncService;
    private transient SwitchRuleService switchRuleService;
    private transient CreateLagPortService createLagPortService;
    private transient UpdateLagPortService updateLagPortService;
    private transient DeleteLagPortService deleteLagPortService;

    private transient Map<String, MessageCookie> timeoutDispatchMap;
    private transient Map<MessageCookie, SwitchManagerHubService> serviceRegistry;

    private LifecycleEvent deferredShutdownEvent;

    public SwitchManagerHub(HubBolt.Config hubConfig, PersistenceManager persistenceManager,
                            SwitchManagerTopologyConfig topologyConfig,
                            RuleManagerConfig ruleManagerConfig) {
        super(persistenceManager, hubConfig);
        this.topologyConfig = topologyConfig;
        this.ruleManagerConfig = ruleManagerConfig;

        enableMeterRegistry("kilda.switch_validate", StreamType.HUB_TO_METRICS_BOLT.name());
    }

    @Override
    public void init() {
        super.init();

        LagPortOperationConfig config = new LagPortOperationConfig(
                persistenceManager.getRepositoryFactory(), persistenceManager.getTransactionManager(),
                topologyConfig.getBfdPortOffset(), topologyConfig.getBfdPortMaxNumber(),
                topologyConfig.getLagPortOffset(), topologyConfig.getLagPortMaxNumber(),
                topologyConfig.getLagPortPoolChunksCount(), topologyConfig.getLagPortPoolCacheSize());
        log.info("LAG logical ports service config: {}", config);

        timeoutDispatchMap = new HashMap<>();

        serviceRegistry = new HashMap<>();

        RuleManager ruleManager = new RuleManagerImpl(ruleManagerConfig);
        // Service name are used by service registry will be used as part of the produced message cookies and as a
        // result as delivery tag on response dispatching. This means that it must be unique across this bolt/class or
        // response dispatching will fail.
        validateService = registerService(serviceRegistry, "switch-validate", this,
                carrier -> new SwitchValidateService(
                        carrier, persistenceManager,
                        new ValidationServiceImpl(persistenceManager, new RuleManagerImpl(ruleManagerConfig))));
        SwitchSyncConfig syncConfig = new SwitchSyncConfig(topologyConfig.getOfCommandsBatchSize());
        syncService = registerService(
                serviceRegistry, "switch-sync", this,
                carrier -> new SwitchSyncService(carrier, persistenceManager, syncConfig));
        switchRuleService = registerService(
                serviceRegistry, "switch-rules", this,
                carrier -> new SwitchRuleService(carrier, persistenceManager, ruleManager));
        createLagPortService = registerService(
                serviceRegistry, "lag-create", this, carrier -> new CreateLagPortService(carrier, config));
        updateLagPortService = registerService(
                serviceRegistry, "lag-update", this, carrier -> new UpdateLagPortService(carrier, config));
        deleteLagPortService = registerService(
                serviceRegistry, "lag-delete", this, carrier -> new DeleteLagPortService(carrier, config));
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (HeavyOperationBolt.ID.equals(input.getSourceComponent())) {
            dispatchResponse(input);
        } else {
            super.handleInput(input);
        }
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        if (!active) {
            log.info("Switch Manager Topology is inactive");
            return;
        }

        String requestKey = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        CommandMessage message = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD, CommandMessage.class);

        CommandData request = message.getData();
        try {
            if (! dispatchRequest(requestKey, request)) {
                unhandledInput(input);
            }
        } catch (SwitchManagerException e) {
            log.error("Unable to handle request {} with key {} - {}", request, requestKey, e.getMessage());
            errorResponse(requestKey, e.getError(), "Unable to handle switch manager request", e.getMessage());
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        dispatchResponse(input);
    }

    @Override
    protected void onTimeout(String key, Tuple tuple) throws PipelineException {
        MessageCookie route = timeoutDispatchMap.remove(key);
        if (route != null) {
            dispatchTimeout(route);
        } else {
            log.info(
                    "Ignoring timeout notification for request key \"{}\"- there is no timeout dispatch route found "
                            + "(can happens due to timeout delivery/cancel race)", key);
        }
    }

    private boolean dispatchRequest(String key, CommandData data) {
        if (data instanceof SwitchValidateRequest) {
            dispatchRequest(
                    validateService, key,
                    service -> service.handleSwitchValidateRequest(key, (SwitchValidateRequest) data));
        } else if (data instanceof SwitchRulesDeleteRequest) {
            dispatchRequest(
                    switchRuleService, key, service -> service.deleteRules(key, (SwitchRulesDeleteRequest) data));
        } else if (data instanceof SwitchRulesInstallRequest) {
            dispatchRequest(
                    switchRuleService, key, service -> service.installRules(key, (SwitchRulesInstallRequest) data));
        } else if (data instanceof CreateLagPortRequest) {
            dispatchRequest(
                    createLagPortService, key,
                    service -> service.handleCreateLagRequest(key, (CreateLagPortRequest) data));
        } else if (data instanceof UpdateLagPortRequest) {
            dispatchRequest(updateLagPortService, key, service -> service.update(key, (UpdateLagPortRequest) data));
        } else if (data instanceof DeleteLagPortRequest) {
            dispatchRequest(
                    deleteLagPortService, key,
                    service -> service.handleDeleteLagRequest(key, (DeleteLagPortRequest) data));
        } else {
            return false;
        }
        return true;
    }

    private <S extends SwitchManagerHubService> void dispatchRequest(S service, String requestKey, Consumer<S> action) {
        timeoutDispatchMap.put(requestKey, service.getCarrier().newDispatchRoute(requestKey));
        action.accept(service);
    }

    private SwitchManagerHubService getService(MessageCookie cookie) throws MessageDispatchException {
        SwitchManagerHubService service = serviceRegistry.get(cookie);
        if (service == null) {
            throw new MessageDispatchException(cookie);
        }
        return service;
    }

    private void dispatchResponse(Tuple input) throws PipelineException {
        Object payload = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD, Object.class);
        if (payload instanceof Message) {
            Message message = (Message) payload;
            dispatchResponse(message, input);
        } else if (payload instanceof AbstractMessage) {
            AbstractMessage abstractMessage = (AbstractMessage) payload;
            dispatchResponse(abstractMessage);
        } else {
            unhandledInput(input);
        }
    }

    private void dispatchResponse(Message message, Tuple input) {
        MessageCookie cookie = message.getCookie();
        if (cookie == null) {
            log.error("There is no message cookie in response, can't determine target service (response: {})", message);
            return;
        }

        try {
            SwitchManagerHubService service = getService(cookie);
            if (message instanceof InfoMessage) {
                if (HeavyOperationBolt.ID.equals(input.getSourceComponent())) {
                    service.dispatchHeavyOperationMessage(((InfoMessage) message).getData(), cookie.getNested());
                } else {
                    service.dispatchWorkerMessage(((InfoMessage) message).getData(), cookie.getNested());
                }
            } else if (message instanceof ErrorMessage) {
                service.dispatchErrorMessage(((ErrorMessage) message).getData(), cookie.getNested());
            } else {
                throw new UnexpectedInputException(message);
            }
        } catch (MessageDispatchException e) {
            log.warn("Unable to route worker response {}: {}", message, e.getMessage(message.getCookie()));
        } catch (UnexpectedInputException e) {
            log.error("{}", e.getMessage(message.getCookie()), e);
        }
    }

    private void dispatchResponse(AbstractMessage message) {
        MessageCookie cookie = message.getMessageCookie();
        if (cookie == null) {
            log.error("There is no message cookie in response, can't determine target service (response: {})", message);
            return;
        }

        try {
            SwitchManagerHubService service = getService(cookie);
            if (message instanceof SpeakerCommandResponse) {
                service.dispatchWorkerMessage((SpeakerCommandResponse) message, cookie.getNested());
            } else {
                throw new UnexpectedInputException(message);
            }
        } catch (MessageDispatchException e) {
            log.warn("Unable to route worker response {}: {}", message, e.getMessage(message.getMessageCookie()));
        } catch (UnexpectedInputException e) {
            log.error("{}", e.getMessage(message.getMessageCookie()), e);
        }
    }

    private void dispatchTimeout(MessageCookie cookie) {
        SwitchManagerHubService service = serviceRegistry.get(cookie);
        if (service == null) {
            log.error("Unable to dispatch timeout into any service using cookie: {}", cookie);
            return;
        }

        try {
            service.timeout(cookie.getNested());
        } catch (MessageDispatchException e) {
            log.info("There is no handler to process timeout notification: {}", cookie);
        }
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        if (validateService.deactivate() && syncService.deactivate() && switchRuleService.deactivate()
                && createLagPortService.deactivate() && deleteLagPortService.deactivate()) {
            return true;
        }
        deferredShutdownEvent = event;
        return false;
    }

    @Override
    protected void activate() {
        validateService.activate();
        syncService.activate();
        switchRuleService.activate();
        createLagPortService.activate();
        deleteLagPortService.activate();
    }

    @Override
    public void cancelTimeoutCallback(String key) {
        timeoutDispatchMap.remove(key);
        cancelCallback(key);
    }

    @Override
    public MessageCookie newDispatchRoute(String requestKey) {
        return new MessageCookie(requestKey);
    }

    @Override
    public void sendCommandToSpeaker(String key, CommandData command) {
        // will never be used, because of carrier decorator
        sendCommandToSpeaker(command, new MessageCookie(key));
    }

    @Override
    public void sendCommandToSpeaker(CommandData command, @NonNull MessageCookie cookie) {
        sendCommandToSpeaker(cookie.toString(), command, cookie);
    }

    public void sendCommandToSpeaker(String requestKey, CommandData command, MessageCookie cookie) {
        emit(SpeakerWorkerBolt.INCOME_STREAM, getCurrentTuple(), makeWorkerTuple(requestKey, command, cookie));
    }

    @Override
    public void runHeavyOperation(String key, SwitchId switchId) {
        // will never be used, because of carrier decorator
        runHeavyOperation(switchId, new MessageCookie(key));
    }

    public void runHeavyOperation(SwitchId switchId, @NonNull MessageCookie messageCookie) {
        emit(HeavyOperationBolt.INCOME_STREAM, getCurrentTuple(), makeHeavyOperationTuple(switchId, messageCookie));
    }

    @Override
    public void sendOfCommandsToSpeaker(String key, List<OfCommand> commands, OfCommandAction action,
                                        SwitchId switchId) {
        // will never be user, because of carrier decorator
        sendOfCommandsToSpeaker(commands, action, switchId, new MessageCookie(key));
    }

    @Override
    public void sendOfCommandsToSpeaker(List<OfCommand> commands, OfCommandAction action, SwitchId switchId,
                                        @NonNull MessageCookie cookie) {
        sendOfCommandsToSpeaker(cookie.toString(), commands, action, switchId, cookie);
    }

    private void sendOfCommandsToSpeaker(String requestKey, List<OfCommand> commands, OfCommandAction action,
                                        SwitchId switchId, MessageCookie cookie) {
        BaseSpeakerCommandsRequest request = getRequest(requestKey, commands, action, switchId);

        emit(SpeakerWorkerBolt.OF_COMMANDS_INCOME_STREAM, getCurrentTuple(),
                makeWorkerTuple(requestKey, request, cookie));
    }

    private BaseSpeakerCommandsRequest getRequest(String requestKey, List<OfCommand> commands, OfCommandAction action,
                                                  SwitchId switchId) {
        MessageContext messageContext = new MessageContext(requestKey, getCommandContext().getCorrelationId());
        UUID commandId = UUID.randomUUID();
        switch (action) {
            case INSTALL:
                return InstallSpeakerCommandsRequest.builder()
                        .messageContext(messageContext)
                        .switchId(switchId)
                        .commandId(commandId)
                        .commands(commands)
                        .build();
            case INSTALL_IF_NOT_EXIST:
                return InstallSpeakerCommandsRequest.builder()
                        .messageContext(messageContext)
                        .switchId(switchId)
                        .commandId(commandId)
                        .commands(commands)
                        .failIfExists(false)
                        .build();
            case MODIFY:
                return new ModifySpeakerCommandsRequest(messageContext, switchId, commandId, commands);
            case DELETE:
                return new DeleteSpeakerCommandsRequest(messageContext, switchId, commandId, commands);
            default:
                throw new IllegalStateException(format("Unknown OpenFlow command type %s", action));
        }
    }

    @Override
    public void response(String key, Message message) {
        emit(NORTHBOUND_STREAM_ID, getCurrentTuple(), makeNorthboundTuple(key, message));
    }

    @Override
    public void response(String key, InfoData payload) {
        InfoMessage message = new InfoMessage(payload, System.currentTimeMillis(), key);
        response(key, message);
    }

    @Override
    public void errorResponse(String key, ErrorType error, String message, String description) {
        ErrorData payload = new ErrorData(error, message, description);
        response(key, new ErrorMessage(payload, System.currentTimeMillis(), key));
    }

    @Override
    public void runSwitchSync(String key, SwitchValidateRequest request, ValidationResult validationResult) {
        syncService.handleSwitchSync(key, request, validationResult);
    }

    @Override
    public void sendInactive() {
        if (validateService.isAllOperationsCompleted()
                && syncService.isAllOperationsCompleted()
                && switchRuleService.isAllOperationsCompleted()
                && createLagPortService.isAllOperationsCompleted()
                && deleteLagPortService.isAllOperationsCompleted()) {
            getOutput().emit(ZOOKEEPER_STREAM_ID, new Values(deferredShutdownEvent, getCommandContext()));
            deferredShutdownEvent = null;
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(SpeakerWorkerBolt.INCOME_STREAM, WORKER_STREAM_FIELDS);
        declarer.declareStream(SpeakerWorkerBolt.OF_COMMANDS_INCOME_STREAM, WORKER_STREAM_FIELDS);
        declarer.declareStream(NORTHBOUND_STREAM_ID, NORTHBOUND_STREAM_FIELDS);
        declarer.declareStream(ZOOKEEPER_STREAM_ID, ZOOKEEPER_STREAM_FIELDS);
        declarer.declareStream(HeavyOperationBolt.INCOME_STREAM, HEAVY_OPERATION_STREAM_FIELDS);
    }

    private Values makeWorkerTuple(String key, CommandData payload, MessageCookie cookie) {
        return new Values(KeyProvider.generateChainedKey(key), payload, cookie, getCommandContext());
    }

    private Values makeWorkerTuple(String key, BaseSpeakerCommandsRequest payload, MessageCookie cookie) {
        return new Values(KeyProvider.generateChainedKey(key), payload, cookie, getCommandContext());
    }

    private Values makeHeavyOperationTuple(SwitchId switchId, MessageCookie cookie) {
        return new Values(cookie.getValue(), switchId, cookie, getCommandContext());
    }

    private Values makeNorthboundTuple(String key, Message payload) {
        return new Values(key, payload);
    }

    private static <S extends SwitchManagerHubService> S registerService(
            Map<MessageCookie, SwitchManagerHubService> registry, String name, SwitchManagerCarrier targetCarrier,
            Function<SwitchManagerCarrier, S> provider) {
        S service = provider.apply(new SwitchManagerCarrierCookieDecorator(targetCarrier, name));
        registry.put(new MessageCookie(name), service);
        return service;
    }

    public enum OfCommandAction {
        INSTALL,
        INSTALL_IF_NOT_EXIST,
        MODIFY,
        DELETE
    }
}
