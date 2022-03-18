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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_METRICS_BOLT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_PING_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_SPEAKER_WORKER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_STATS_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.PeriodicPingCommand;
import org.openkilda.messaging.command.yflow.YFlowPartialUpdateRequest;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.server42.control.messaging.flowrtt.ActivateFlowMonitoringInfoData;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.bolts.FlowUpdateHubBolt.FlowUpdateConfig;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateService;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowUpdateService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.experimental.SuperBuilder;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class YFlowUpdateHubBolt extends HubBolt implements FlowUpdateHubCarrier {
    private final YFlowUpdateConfig yflowUpdateConfig;
    private final FlowUpdateConfig flowUpdateConfig;
    private final PathComputerConfig pathComputerConfig;
    private final FlowResourcesConfig flowResourcesConfig;
    private final RuleManagerConfig ruleManagerConfig;

    private transient YFlowUpdateService yflowUpdateService;
    private transient FlowUpdateService basicFlowUpdateService;
    private String currentKey;

    private LifecycleEvent deferredShutdownEvent;

    public YFlowUpdateHubBolt(@NonNull YFlowUpdateConfig yflowUpdateConfig, @NonNull FlowUpdateConfig flowUpdateConfig,
                              @NonNull PersistenceManager persistenceManager,
                              @NonNull PathComputerConfig pathComputerConfig,
                              @NonNull FlowResourcesConfig flowResourcesConfig,
                              @NonNull RuleManagerConfig ruleManagerConfig) {
        super(persistenceManager, yflowUpdateConfig);

        this.yflowUpdateConfig = yflowUpdateConfig;
        this.flowUpdateConfig = flowUpdateConfig;
        this.pathComputerConfig = pathComputerConfig;
        this.flowResourcesConfig = flowResourcesConfig;
        this.ruleManagerConfig = ruleManagerConfig;

        enableMeterRegistry("kilda.y_flow_update", HUB_TO_METRICS_BOLT.name());
    }

    @Override
    protected void init() {
        FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        AvailableNetworkFactory availableNetworkFactory =
                new AvailableNetworkFactory(pathComputerConfig, persistenceManager.getRepositoryFactory());
        PathComputer pathComputer =
                new PathComputerFactory(pathComputerConfig, availableNetworkFactory).getPathComputer();
        RuleManager ruleManager = new RuleManagerImpl(ruleManagerConfig);

        basicFlowUpdateService = new FlowUpdateService(
                new FlowUpdateHubCarrierIsolatingResponsesAndLifecycleEvents(this),
                persistenceManager, pathComputer, resourcesManager,
                flowUpdateConfig.getPathAllocationRetriesLimit(), flowUpdateConfig.getPathAllocationRetryDelay(),
                flowUpdateConfig.getResourceAllocationRetriesLimit(), flowUpdateConfig.getSpeakerCommandRetriesLimit());

        yflowUpdateService = new YFlowUpdateService(this, persistenceManager, pathComputer, resourcesManager,
                ruleManager, basicFlowUpdateService, yflowUpdateConfig.getResourceAllocationRetriesLimit(),
                yflowUpdateConfig.getSpeakerCommandRetriesLimit(), yflowUpdateConfig.getPrefixForGeneratedYFlowId(),
                yflowUpdateConfig.getPrefixForGeneratedSubFlowId());
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        if (yflowUpdateService.deactivate() && basicFlowUpdateService.deactivate()) {
            return true;
        }
        deferredShutdownEvent = event;
        return false;
    }

    @Override
    protected void activate() {
        basicFlowUpdateService.activate();
        yflowUpdateService.activate();
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        currentKey = pullKey(input);
        CommandData payload = pullValue(input, FIELD_ID_PAYLOAD, CommandData.class);
        try {
            if (payload instanceof YFlowRequest) {
                yflowUpdateService.handleRequest(currentKey, pullContext(input), (YFlowRequest) payload);
            } else if (payload instanceof YFlowPartialUpdateRequest) {
                yflowUpdateService.handlePartialUpdateRequest(currentKey, pullContext(input),
                        (YFlowPartialUpdateRequest) payload);
            } else {
                unhandledInput(input);
            }
        } catch (DuplicateKeyException e) {
            log.error("Failed to handle a request with key {}. {}", currentKey, e.getMessage());
        } catch (FlowProcessingException e) {
            ErrorData errorData = new ErrorData(e.getErrorType(), "Y-flow update error", e.getMessage());
            CommandContext commandContext = getCommandContext();
            ErrorMessage errorMessage = new ErrorMessage(errorData, commandContext.getCreateTime(),
                    commandContext.getCorrelationId());
            sendNorthboundResponse(errorMessage);
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        String operationKey = pullKey(input);
        currentKey = KeyProvider.getParentKey(operationKey);
        SpeakerResponse speakerResponse = pullValue(input, FIELD_ID_PAYLOAD, SpeakerResponse.class);
        try {
            yflowUpdateService.handleAsyncResponse(currentKey, speakerResponse);
        } catch (UnknownKeyException e) {
            log.error("Received a response with unknown key {}.", currentKey);
        }
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        currentKey = key;
        try {
            yflowUpdateService.handleTimeout(key);
        } catch (UnknownKeyException e) {
            log.error("Failed to handle a timeout event for unknown key {}.", currentKey);
        }
    }

    @Override
    public void sendSpeakerRequest(@NonNull SpeakerRequest command) {
        String commandKey = KeyProvider.joinKeys(command.getCommandId().toString(), currentKey);

        Values values = new Values(commandKey, command);
        emitWithContext(HUB_TO_SPEAKER_WORKER.name(), getCurrentTuple(), values);
    }

    @Override
    public void sendNorthboundResponse(@NonNull Message message) {
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
    }

    @Override
    public void sendHistoryUpdate(@NonNull FlowHistoryHolder historyHolder) {
        InfoMessage message = new InfoMessage(historyHolder, getCommandContext().getCreateTime(),
                getCommandContext().getCorrelationId());
        emitWithContext(Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(historyHolder.getTaskId(), message));
    }

    @Override
    public void sendHubSwapEndpointsResponse(@NonNull Message message) {
        // do not need to send response to swap endpoints hub
    }

    @Override
    public void cancelTimeoutCallback(String key) {
        cancelCallback(key);
    }

    @Override
    public void sendInactive() {
        getOutput().emit(ZkStreams.ZK.toString(), new Values(deferredShutdownEvent, getCommandContext()));
        deferredShutdownEvent = null;
    }

    @Override
    public void sendPeriodicPingNotification(String flowId, boolean enabled) {
        PeriodicPingCommand payload = new PeriodicPingCommand(flowId, enabled);
        Message message = new CommandMessage(payload, getCommandContext().getCreateTime(),
                getCommandContext().getCorrelationId());
        emitWithContext(Stream.HUB_TO_PING_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
    }

    @Override
    public void sendActivateFlowMonitoring(@NonNull RequestedFlow flow) {
        ActivateFlowMonitoringInfoData payload = RequestedFlowMapper.INSTANCE.toActivateFlowMonitoringInfoData(flow);

        Message message = new InfoMessage(payload, getCommandContext().getCreateTime(),
                getCommandContext().getCorrelationId());
        emitWithContext(HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(flow.getFlowId(), message));
    }

    @Override
    public void sendNotifyFlowMonitor(@NonNull CommandData flowCommand) {
        String correlationId = getCommandContext().getCorrelationId();
        Message message = new CommandMessage(flowCommand, System.currentTimeMillis(), correlationId);

        emitWithContext(HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(correlationId, message));
    }

    @Override
    public void sendNotifyFlowStats(@NonNull UpdateFlowPathInfo flowPathInfo) {
        Message message = new InfoMessage(flowPathInfo, System.currentTimeMillis(),
                getCommandContext().getCorrelationId());

        emitWithContext(HUB_TO_STATS_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(flowPathInfo.getFlowId(), message));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(HUB_TO_SPEAKER_WORKER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_PING_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(ZkStreams.ZK.toString(),
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
        declarer.declareStream(HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_STATS_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }

    @Getter
    @SuperBuilder
    public static class YFlowUpdateConfig extends Config {
        private int resourceAllocationRetriesLimit;
        private int speakerCommandRetriesLimit;
        @Builder.Default
        private String prefixForGeneratedYFlowId = "y-";
        @Builder.Default
        private String prefixForGeneratedSubFlowId = "ys-";

        public YFlowUpdateConfig(String requestSenderComponent, String workerComponent, String lifeCycleEventComponent,
                                 int timeoutMs, boolean autoAck, int resourceAllocationRetriesLimit,
                                 int speakerCommandRetriesLimit) {
            super(requestSenderComponent, workerComponent, lifeCycleEventComponent, timeoutMs, autoAck);
            this.resourceAllocationRetriesLimit = resourceAllocationRetriesLimit;
            this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        }
    }

    @AllArgsConstructor
    private static class FlowUpdateHubCarrierIsolatingResponsesAndLifecycleEvents implements FlowUpdateHubCarrier {
        @Delegate(excludes = CarrierMethodsToIsolateResponsesAndLifecycleEvents.class)
        FlowUpdateHubCarrier delegate;

        @Override
        public void sendNorthboundResponse(Message message) {
            // Isolating, so nothing to do.
        }

        @Override
        public void sendInactive() {
            // Isolating, so nothing to do.
        }
    }

    private interface CarrierMethodsToIsolateResponsesAndLifecycleEvents {
        void sendNorthboundResponse(Message message);

        void sendInactive();
    }
}
