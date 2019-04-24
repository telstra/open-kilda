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

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.FeatureTogglesBaseRequest;
import org.openkilda.messaging.nbtopology.request.FlowValidationRequest;
import org.openkilda.messaging.nbtopology.request.FlowsBaseRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowHistoryRequest;
import org.openkilda.messaging.nbtopology.request.GetPathsRequest;
import org.openkilda.messaging.nbtopology.request.LinksBaseRequest;
import org.openkilda.messaging.nbtopology.request.MeterModifyRequest;
import org.openkilda.messaging.nbtopology.request.SwitchesBaseRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.commands.RemoveKeyRouterBolt;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.Map;

public class RouterBolt extends AbstractBolt {
    public static final String INCOME_STREAM = "router.bolt.stream";

    private Map<String, String> streams = new HashMap<>();

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

        String parentKey = KeyProvider.getParentKey(key);
        if (streams.keySet().contains(parentKey)) {
            processResponse(input, key, parentKey, message);
        } else {
            processRequest(input, key, message);
        }

    }

    private void processResponse(Tuple input, String key, String parentKey, Message message) {
        if (message instanceof CommandMessage) {
            CommandData data = ((CommandMessage) message).getData();
            if (data instanceof RemoveKeyRouterBolt) {
                streams.remove(((RemoveKeyRouterBolt) data).getKey());
            }
        } else {
            emitWithContext(streams.get(parentKey), input, new Values(key, message));
        }
    }

    private void processRequest(Tuple input, String key, Message message) {
        if (message instanceof CommandMessage) {
            log.debug("Received command message {}", message);
            CommandMessage command = (CommandMessage) message;
            CommandData data = command.getData();

            if (data instanceof BaseRequest) {
                BaseRequest baseRequest = (BaseRequest) data;
                processRequest(input, key, baseRequest, message.getCorrelationId());
            }
        } else if (message instanceof InfoMessage) {
            log.debug("Received info message {}", message);
            InfoMessage info = (InfoMessage) message;
            InfoData data = info.getData();
            processRequest(input, data, message.getCorrelationId());
        } else if (message instanceof ErrorMessage) {
            log.debug("Received error message {}", message);
            ErrorMessage error = (ErrorMessage) message;
            ErrorData data = error.getData();
            processRequest(input, data, message.getCorrelationId());
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, String key, BaseRequest request, String correlationId) {
        if (request instanceof SwitchesBaseRequest) {
            getOutput().emit(StreamType.SWITCH.toString(), input, new Values(request, correlationId));
        } else if (request instanceof LinksBaseRequest) {
            getOutput().emit(StreamType.ISL.toString(), input, new Values(request, correlationId));
        } else if (request instanceof FlowsBaseRequest) {
            getOutput().emit(StreamType.FLOW.toString(), input, new Values(request, correlationId));
        } else if (request instanceof FeatureTogglesBaseRequest) {
            getOutput().emit(StreamType.FEATURE_TOGGLES.toString(), input, new Values(request, correlationId));
        } else if (request instanceof GetPathsRequest) {
            getOutput().emit(StreamType.PATHS.toString(), input, new Values(request, correlationId));
        } else if (request instanceof GetFlowHistoryRequest) {
            getOutput().emit(StreamType.HISTORY.toString(), input, new Values(request, correlationId));
        } else if (request instanceof FlowValidationRequest) {
            streams.put(key, StreamType.FLOW_VALIDATION_WORKER.toString());
            emitWithContext(FlowValidationHubBolt.INCOME_STREAM, input, new Values(correlationId, request));
        } else if (request instanceof MeterModifyRequest) {
            streams.put(key, StreamType.METER_MODIFY_WORKER.toString());
            emitWithContext(FlowMeterModifyHubBolt.INCOME_STREAM, input, new Values(correlationId, request));
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, InfoData data, String correlationId) {
        if (data instanceof SwitchFlowEntries) {
            getOutput().emit(StreamType.VALIDATION.toString(), input, new Values(data, correlationId));
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, ErrorData data, String correlationId) {
        getOutput().emit(StreamType.ERROR.toString(), input, new Values(data, correlationId));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(PersistenceOperationsBolt.FIELD_ID_REQUEST,
                PersistenceOperationsBolt.FIELD_ID_CORELLATION_ID);
        declarer.declareStream(StreamType.SWITCH.toString(), fields);
        declarer.declareStream(StreamType.ISL.toString(), fields);
        declarer.declareStream(StreamType.FLOW.toString(), fields);
        declarer.declareStream(StreamType.FEATURE_TOGGLES.toString(), fields);
        declarer.declareStream(StreamType.PATHS.toString(), fields);
        declarer.declareStream(StreamType.HISTORY.toString(), fields);

        declarer.declareStream(StreamType.VALIDATION.toString(),
                new Fields(SwitchValidationsBolt.FIELD_ID_REQUEST,
                        SwitchValidationsBolt.FIELD_ID_CORELLATION_ID));

        declarer.declareStream(StreamType.ERROR.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));

        declarer.declareStream(FlowValidationHubBolt.INCOME_STREAM, MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(FlowMeterModifyHubBolt.INCOME_STREAM, MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(StreamType.FLOW_VALIDATION_WORKER.toString(), MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(StreamType.METER_MODIFY_WORKER.toString(), MessageTranslator.STREAM_FIELDS);
    }
}
