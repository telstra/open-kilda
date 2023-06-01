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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_METRICS_BOLT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_PING_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_REROUTE_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_SPEAKER_WORKER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.haflow.HaFlowPathSwapRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.service.FlowPathSwapHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowPathSwapService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class HaFlowPathSwapHubBolt extends HubBolt implements FlowPathSwapHubCarrier {

    private final HaFlowPathSwapConfig config;
    private final RuleManagerConfig ruleManagerConfig;

    private transient HaFlowPathSwapService service;
    private String currentKey;

    private LifecycleEvent deferredShutdownEvent;

    public HaFlowPathSwapHubBolt(
            @NonNull HaFlowPathSwapHubBolt.HaFlowPathSwapConfig config, @NonNull PersistenceManager persistenceManager,
            @NonNull RuleManagerConfig ruleManagerConfig) {
        super(persistenceManager, config);

        this.config = config;
        this.ruleManagerConfig = ruleManagerConfig;

        enableMeterRegistry("kilda.ha_flow_pathswap", HUB_TO_METRICS_BOLT.name());
    }

    @Override
    protected void init() {
        RuleManager ruleManager = new RuleManagerImpl(ruleManagerConfig);
        service = new HaFlowPathSwapService(
                this, persistenceManager, ruleManager, config.getSpeakerCommandRetriesLimit());
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
        currentKey = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        HaFlowPathSwapRequest payload = (HaFlowPathSwapRequest) input.getValueByField(FIELD_ID_PAYLOAD);
        try {
            service.handleRequest(currentKey, pullContext(input), payload.getHaFlowId());
        } catch (DuplicateKeyException e) {
            log.error("Failed to handle a request with key {}. {}", currentKey, e.getMessage());
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) {
        String operationKey = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        currentKey = KeyProvider.getParentKey(operationKey);
        SpeakerCommandResponse flowResponse = (SpeakerCommandResponse) input.getValueByField(FIELD_ID_PAYLOAD);
        service.handleAsyncResponse(currentKey, flowResponse);
    }

    @Override
    public void onTimeout(@NonNull String key, Tuple tuple) {
        currentKey = key;
        service.handleTimeout(key);
    }

    @Override
    public void sendInactive() {
        getOutput().emit(ZkStreams.ZK.toString(), new Values(deferredShutdownEvent, getCommandContext()));
        deferredShutdownEvent = null;
    }

    @Override
    public void sendPeriodicPingNotification(String haFlowId, boolean enabled) {
        //TODO implement periodic pings https://github.com/telstra/open-kilda/issues/5153
        log.info("Periodic pings are not implemented for ha-flow swap path operation yet. Skipping for the ha-flow {}",
                haFlowId);
    }

    @Override
    public void sendNotifyFlowMonitor(CommandData haFlowCommand) {
        String correlationId = getCommandContext().getCorrelationId();
        Message message = new CommandMessage(haFlowCommand, System.currentTimeMillis(), correlationId);
        emitWithContext(HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(correlationId, message));
    }

    @Override
    public void sendSpeakerRequest(SpeakerRequest command) {
        String commandKey = KeyProvider.joinKeys(command.getCommandId().toString(), currentKey);
        Values values = new Values(commandKey, command);
        emitWithContext(HUB_TO_SPEAKER_WORKER.name(), getCurrentTuple(), values);
    }

    @Override
    public void sendNorthboundResponse(Message message) {
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
    }

    @Override
    public void sendHistoryUpdate(FlowHistoryHolder historyHolder) {
        //TODO check history https://github.com/telstra/open-kilda/issues/5169
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
    public void sendPathSwapResultStatus(String flowId, boolean success, String correlationId) {
        //TODO implement https://github.com/telstra/open-kilda/issues/5061
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(HUB_TO_SPEAKER_WORKER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_PING_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_REROUTE_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(ZkStreams.ZK.toString(),
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
        declarer.declareStream(HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }

    @Getter
    @SuperBuilder
    public static class HaFlowPathSwapConfig extends Config {
        private final int speakerCommandRetriesLimit;
    }
}
