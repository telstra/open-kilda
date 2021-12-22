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

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_METRICS_BOLT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_SPEAKER_WORKER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowValidationRequest;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.ArrayList;
import java.util.List;

public class FlowValidationHubBolt extends HubBolt implements FlowValidationHubCarrier {

    private final FlowResourcesConfig flowResourcesConfig;
    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;

    private transient FlowValidationHubService service;
    private String currentKey;

    private LifecycleEvent deferredShutdownEvent;

    public FlowValidationHubBolt(@NonNull Config config, @NonNull PersistenceManager persistenceManager,
                                 @NonNull FlowResourcesConfig flowResourcesConfig,
                                 long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        super(persistenceManager, config);

        this.flowResourcesConfig = flowResourcesConfig;
        this.flowMeterMinBurstSizeInKbits = flowMeterMinBurstSizeInKbits;
        this.flowMeterBurstCoefficient = flowMeterBurstCoefficient;

        enableMeterRegistry("kilda.flow_validation", HUB_TO_METRICS_BOLT.name());
    }

    @Override
    public void init() {
        service = new FlowValidationHubService(this, persistenceManager, flowResourcesConfig,
                flowMeterMinBurstSizeInKbits, flowMeterBurstCoefficient);
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
        FlowValidationRequest payload = pullValue(input, FIELD_ID_PAYLOAD, FlowValidationRequest.class);
        try {
            service.handleFlowValidationRequest(currentKey, getCommandContext(), payload);
        } catch (DuplicateKeyException e) {
            log.error("Failed to handle a request with key {}. {}", currentKey, e.getMessage());
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        String operationKey = pullKey(input);
        currentKey = KeyProvider.getParentKey(operationKey);
        MessageData messageData = pullValue(input, FIELD_ID_PAYLOAD, MessageData.class);
        try {
            service.handleAsyncResponse(currentKey, messageData);
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
    public void sendSpeakerRequest(@NonNull String flowId, @NonNull CommandData commandData) {
        String commandId = KeyProvider.generateKey();
        String commandKey = KeyProvider.joinKeys(commandId, currentKey);
        Values values = new Values(commandKey, new CommandMessage(commandData, System.currentTimeMillis(), commandKey));
        emitWithContext(HUB_TO_SPEAKER_WORKER.name(), getCurrentTuple(), values);
    }

    @Override
    public void sendNorthboundResponse(@NonNull List<? extends InfoData> messageData) {
        // The validation process sends a response to NB when CommandContext represents a worker response, so
        // we need to use "parent" key to override the CommandContext.correlationId.
        buildResponseMessages(messageData, currentKey)
                .forEach(message -> emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(),
                        new Values(currentKey, message)));
    }

    @Override
    public void sendNorthboundResponse(@NonNull Message message) {
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
    }

    private List<Message> buildResponseMessages(List<? extends InfoData> responseData, String requestId) {
        List<Message> messages = new ArrayList<>(responseData.size());
        if (CollectionUtils.isEmpty(responseData)) {
            messages.add(new ChunkedInfoMessage(null, System.currentTimeMillis(), requestId, requestId, 0));
        } else {
            int i = 0;
            for (InfoData data : responseData) {
                Message message = new ChunkedInfoMessage(data, System.currentTimeMillis(), requestId, i++,
                        responseData.size());
                messages.add(message);
            }
        }
        return messages;
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
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(HUB_TO_SPEAKER_WORKER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(ZkStreams.ZK.toString(),
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
