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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.nbtopology.request.FlowValidationRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.commands.RemoveKeyRouterBolt;
import org.openkilda.wfm.topology.nbworker.services.FlowValidationHubService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;
import java.util.Map;

public class FlowValidationHubBolt extends HubBolt {
    public static final String ID = "flow.validation.hub";
    public static final String INCOME_STREAM = "flow.validation.stream";
    private static final int TIMEOUT_MS = 10000;
    private static final boolean AUTO_ACK = true;

    private final PersistenceManager persistenceManager;
    private transient FlowValidationHubService service;

    public FlowValidationHubBolt(String requestSenderComponent, PersistenceManager persistenceManager) {
        super(HubBolt.Config.builder()
                .requestSenderComponent(requestSenderComponent)
                .timeoutMs(TIMEOUT_MS)
                .autoAck(AUTO_ACK)
                .build());
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        service = new FlowValidationHubService(persistenceManager);
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        CommandData data = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, CommandData.class);

        if (data instanceof FlowValidationRequest) {
            service.handleFlowValidationRequest(key, (FlowValidationRequest) data,
                    new FlowValidationHubCarrierImpl(input));
        } else {
            unhandledInput(input);
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        String key = KeyProvider.getParentKey(input.getStringByField(MessageTranslator.FIELD_ID_KEY));
        Message message = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, Message.class);
        service.handleAsyncResponse(key, message);
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        service.handleTaskTimeout(key);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(SpeakerWorkerBolt.INCOME_STREAM, MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(StreamType.ERROR.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
        declarer.declareStream(RouterBolt.INCOME_STREAM, MessageTranslator.STREAM_FIELDS);
        declarer.declare(new Fields(ResponseSplitterBolt.FIELD_ID_RESPONSE,
                ResponseSplitterBolt.FIELD_ID_CORELLATION_ID));
    }

    private class FlowValidationHubCarrierImpl implements FlowValidationHubCarrier {
        private final Tuple tuple;

        FlowValidationHubCarrierImpl(Tuple tuple) {
            this.tuple = tuple;
        }

        @Override
        public void sendCommandToSpeakerWorker(String key, SwitchId switchId) {
            emitWithContext(SpeakerWorkerBolt.INCOME_STREAM, tuple,
                    new Values(KeyProvider.generateChainedKey(key), switchId));
        }

        @Override
        public void sendToResponseSplitterBolt(String key, List<? extends InfoData> message) {
            getOutput().emit(tuple, new Values(message, key));
        }

        @Override
        public void sendToMessageEncoder(String key, ErrorData errorData) {
            getOutput().emit(StreamType.ERROR.toString(), tuple, new Values(errorData, key));
        }

        @Override
        public void endProcessing(String key) {
            cancelCallback(key, tuple);
            removeKeyFromRouterBolt(key);
        }

        private void removeKeyFromRouterBolt(String key) {
            CommandMessage commandMessage =
                    new CommandMessage(new RemoveKeyRouterBolt(key), System.currentTimeMillis(), key);
            emitWithContext(RouterBolt.INCOME_STREAM, tuple, new Values(key, commandMessage));
        }
    }
}
