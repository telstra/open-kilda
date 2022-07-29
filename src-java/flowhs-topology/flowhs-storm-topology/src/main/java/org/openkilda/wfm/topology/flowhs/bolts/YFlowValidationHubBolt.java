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
import org.openkilda.messaging.command.yflow.YFlowValidationRequest;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exceptions.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exceptions.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationService;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubService;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowValidationHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowValidationHubService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;

public class YFlowValidationHubBolt extends HubBolt implements YFlowValidationHubCarrier {

    private final FlowResourcesConfig flowResourcesConfig;
    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;

    private transient FlowValidationHubService flowValidationHubService;
    private transient YFlowValidationHubService yFlowValidationHubService;
    private String currentKey;

    private LifecycleEvent deferredShutdownEvent;

    public YFlowValidationHubBolt(@NonNull Config yFlowValidationConfig, @NonNull PersistenceManager persistenceManager,
                                  @NonNull FlowResourcesConfig flowResourcesConfig,
                                  long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        super(persistenceManager, yFlowValidationConfig);

        this.flowResourcesConfig = flowResourcesConfig;
        this.flowMeterMinBurstSizeInKbits = flowMeterMinBurstSizeInKbits;
        this.flowMeterBurstCoefficient = flowMeterBurstCoefficient;

        enableMeterRegistry("kilda.y_flow_validation", HUB_TO_METRICS_BOLT.name());
    }

    @Override
    public void init() {
        FlowResourcesManager flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        flowValidationHubService = new FlowValidationHubService(
                new FlowValidationHubCarrierIsolatingResponsesAndLifecycleEvents(this),
                persistenceManager, flowResourcesManager, flowMeterMinBurstSizeInKbits, flowMeterBurstCoefficient);
        YFlowValidationService yFlowValidationService = new YFlowValidationService(persistenceManager,
                flowResourcesManager, flowMeterMinBurstSizeInKbits,
                flowMeterBurstCoefficient);
        yFlowValidationHubService = new YFlowValidationHubService(this, persistenceManager,
                flowValidationHubService, yFlowValidationService);
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        if (yFlowValidationHubService.deactivate() && flowValidationHubService.deactivate()) {
            return true;
        }
        deferredShutdownEvent = event;
        return false;
    }

    @Override
    protected void activate() {
        flowValidationHubService.activate();
        yFlowValidationHubService.activate();
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        currentKey = pullKey(input);
        YFlowValidationRequest payload = pullValue(input, FIELD_ID_PAYLOAD, YFlowValidationRequest.class);
        try {
            yFlowValidationHubService.handleRequest(currentKey, getCommandContext(), payload.getYFlowId());
        } catch (DuplicateKeyException e) {
            log.error("Failed to handle a request with key {}. {}", currentKey, e.getMessage());
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        String operationKey = pullKey(input);
        String flowKey = KeyProvider.getParentKey(operationKey);
        String flowId = KeyProvider.getChildKey(flowKey);
        currentKey = KeyProvider.getParentKey(flowKey);
        MessageData messageData = pullValue(input, FIELD_ID_PAYLOAD, MessageData.class);
        try {
            yFlowValidationHubService.handleAsyncResponse(currentKey, flowId, messageData);
        } catch (UnknownKeyException e) {
            log.warn("Received a response with unknown key {}.", currentKey);
        }
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        currentKey = key;
        try {
            yFlowValidationHubService.handleTimeout(key);
        } catch (UnknownKeyException e) {
            log.error("Failed to handle a timeout event for unknown key {}.", currentKey);
        }
    }

    @Override
    public void sendSpeakerRequest(String flowId, @NonNull CommandData commandData) {
        String commandId = KeyProvider.generateKey();
        String commandKey = KeyProvider.joinKeys(commandId, KeyProvider.joinKeys(flowId, currentKey));
        Values values = new Values(commandKey, new CommandMessage(commandData, System.currentTimeMillis(), commandKey));
        emitWithContext(HUB_TO_SPEAKER_WORKER.name(), getCurrentTuple(), values);
    }

    @Override
    public void sendNorthboundResponse(@NonNull Message message) {
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
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

    @AllArgsConstructor
    private static class FlowValidationHubCarrierIsolatingResponsesAndLifecycleEvents
            implements FlowValidationHubCarrier {
        @Delegate(excludes = CarrierMethodsToIsolateResponsesAndLifecycleEvents.class)
        YFlowValidationHubCarrier delegate;

        @Override
        public void sendNorthboundResponse(List<? extends InfoData> messageData) {
            // Isolating, so nothing to do.
        }

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
        void sendNorthboundResponse(List<? extends InfoData> messageData);

        void sendNorthboundResponse(Message message);

        void sendInactive();
    }
}
