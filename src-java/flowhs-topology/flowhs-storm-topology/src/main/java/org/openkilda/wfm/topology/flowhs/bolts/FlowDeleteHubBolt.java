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
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.PeriodicPingCommand;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.server42.control.messaging.flowrtt.DeactivateFlowMonitoringInfoData;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exceptions.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exceptions.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowDeleteHubBolt extends HubBolt implements FlowGenericCarrier {
    private final FlowDeleteConfig config;
    private final FlowResourcesConfig flowResourcesConfig;

    private transient FlowDeleteService service;
    private String currentKey;

    private LifecycleEvent deferredShutdownEvent;

    public FlowDeleteHubBolt(@NonNull FlowDeleteConfig config, @NonNull PersistenceManager persistenceManager,
                             @NonNull FlowResourcesConfig flowResourcesConfig) {
        super(persistenceManager, config);

        this.config = config;
        this.flowResourcesConfig = flowResourcesConfig;

        enableMeterRegistry("kilda.flow_delete", HUB_TO_METRICS_BOLT.name());
    }

    @Override
    protected void init() {
        FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        service = new FlowDeleteService(this, persistenceManager, resourcesManager,
                config.getSpeakerCommandRetriesLimit());
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        if (service.deactivate()) {
            return true;
        }
        deferredShutdownEvent = event;
        return false;
    }

    @Override
    protected void activate() {
        service.activate();
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        currentKey = pullKey(input);
        FlowDeleteRequest request = pullValue(input, FIELD_ID_PAYLOAD, FlowDeleteRequest.class);
        try {
            service.handleRequest(currentKey, getCommandContext(), request.getFlowId());
        } catch (DuplicateKeyException e) {
            log.error("Failed to handle a request with key {}. {}", currentKey, e.getMessage());
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        String operationKey = pullKey(input);
        currentKey = KeyProvider.getParentKey(operationKey);
        SpeakerFlowSegmentResponse flowResponse = pullValue(input, FIELD_ID_PAYLOAD, SpeakerFlowSegmentResponse.class);
        try {
            service.handleAsyncResponse(currentKey, flowResponse);
        } catch (UnknownKeyException e) {
            log.warn("Received a response with unknown key {}.", currentKey);
        }
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        currentKey = key;
        try {
            service.handleTimeout(key);
        } catch (UnknownKeyException e) {
            log.warn("Failed to handle a timeout event for unknown key {}.", currentKey);
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
    public void cancelTimeoutCallback(String key) {
        cancelCallback(key);
    }

    @Override
    public void sendPeriodicPingNotification(String flowId, boolean enabled) {
        PeriodicPingCommand payload = new PeriodicPingCommand(flowId, enabled);
        Message message = new CommandMessage(payload, getCommandContext().getCreateTime(),
                getCommandContext().getCorrelationId());
        emitWithContext(Stream.HUB_TO_PING_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
    }


    @Override
    public void sendDeactivateFlowMonitoring(String flow, SwitchId srcSwitchId, SwitchId dstSwitchId) {
        DeactivateFlowMonitoringInfoData payload = DeactivateFlowMonitoringInfoData.builder()
                .flowId(flow).switchId(dstSwitchId).switchId(srcSwitchId).build();

        Message message = new InfoMessage(payload, getCommandContext().getCreateTime(),
                getCommandContext().getCorrelationId());
        emitWithContext(HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(flow, message));
    }

    @Override
    public void sendInactive() {
        getOutput().emit(ZkStreams.ZK.toString(), new Values(deferredShutdownEvent, getCommandContext()));
        deferredShutdownEvent = null;
    }

    @Override
    public void sendNotifyFlowMonitor(@NonNull CommandData flowCommand) {
        String correlationId = getCommandContext().getCorrelationId();
        Message message = new CommandMessage(flowCommand, System.currentTimeMillis(), correlationId);

        emitWithContext(HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(correlationId, message));
    }

    @Override
    public void sendNotifyFlowStats(@NonNull RemoveFlowPathInfo flowPathInfo) {
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
    public static class FlowDeleteConfig extends Config {
        private int speakerCommandRetriesLimit;

        @Builder(builderMethodName = "flowDeleteBuilder", builderClassName = "flowDeleteBuild")
        public FlowDeleteConfig(String requestSenderComponent, String workerComponent, String lifeCycleEventComponent,
                                int timeoutMs, boolean autoAck,
                                int speakerCommandRetriesLimit) {
            super(requestSenderComponent, workerComponent, lifeCycleEventComponent, timeoutMs, autoAck);
            this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        }
    }
}
