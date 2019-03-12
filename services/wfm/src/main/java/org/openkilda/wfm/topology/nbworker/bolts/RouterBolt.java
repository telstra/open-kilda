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
import org.openkilda.messaging.nbtopology.request.KildaConfigurationBaseRequest;
import org.openkilda.messaging.nbtopology.request.LinksBaseRequest;
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

import java.util.HashSet;
import java.util.Set;

public class RouterBolt extends AbstractBolt {
    public static final String INCOME_STREAM = "router.bolt.stream";
    private Set<String> keys = new HashSet<>();

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

        if (keys.contains(KeyProvider.getParentKey(key))) {
            processFlowValidationRequest(input, key, message);
        } else {
            processRequest(input, key, message);
        }

    }

    private void processFlowValidationRequest(Tuple input, String key, Message message) {
        if (message instanceof CommandMessage) {
            CommandData data = ((CommandMessage) message).getData();
            if (data instanceof RemoveKeyRouterBolt) {
                keys.remove(((RemoveKeyRouterBolt) data).getKey());
            }
        } else {
            emitWithContext(SpeakerWorkerBolt.INCOME_STREAM, input, new Values(key, message));
        }
    }

    private void processRequest(Tuple input, String key, Message message) {
        if (message instanceof CommandMessage) {
            log.debug("Received command message {}", message);
            CommandMessage command = (CommandMessage) message;
            CommandData data = command.getData();

            if (data instanceof BaseRequest) {
                BaseRequest baseRequest = (BaseRequest) data;
                processRequest(input, key, baseRequest);
            }
        } else if (message instanceof InfoMessage) {
            log.debug("Received info message {}", message);
            InfoMessage info = (InfoMessage) message;
            InfoData data = info.getData();
            processRequest(input, data);
        } else if (message instanceof ErrorMessage) {
            log.debug("Received error message {}", message);
            ErrorMessage error = (ErrorMessage) message;
            ErrorData data = error.getData();
            processRequest(input, data);
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, String key, BaseRequest request) {
        if (request instanceof SwitchesBaseRequest) {
            emitWithContext(StreamType.SWITCH.toString(), input, new Values(request));
        } else if (request instanceof LinksBaseRequest) {
            emitWithContext(StreamType.ISL.toString(), input, new Values(request));
        } else if (request instanceof FlowsBaseRequest) {
            emitWithContext(StreamType.FLOW.toString(), input, new Values(request));
        } else if (request instanceof FeatureTogglesBaseRequest) {
            emitWithContext(StreamType.FEATURE_TOGGLES.toString(), input, new Values(request));
        } else if (request instanceof KildaConfigurationBaseRequest) {
            emitWithContext(StreamType.KILDA_CONFIG.toString(), input, new Values(request));
        } else if (request instanceof GetPathsRequest) {
            emitWithContext(StreamType.PATHS.toString(), input, new Values(request));
        } else if (request instanceof GetFlowHistoryRequest) {
            emitWithContext(StreamType.HISTORY.toString(), input, new Values(request));
        } else if (request instanceof FlowValidationRequest) {
            keys.add(key);
            emitWithContext(FlowValidationHubBolt.INCOME_STREAM, input, new Values(key, request));
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, InfoData data) {
        if (data instanceof SwitchFlowEntries) {
            emitWithContext(StreamType.VALIDATION.toString(), input, new Values(data));
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, ErrorData data) {
        emitWithContext(StreamType.ERROR.toString(), input, new Values(data));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields fields = new Fields(PersistenceOperationsBolt.FIELD_ID_REQUEST,
                PersistenceOperationsBolt.FIELD_ID_CONTEXT);
        declarer.declareStream(StreamType.SWITCH.toString(), fields);
        declarer.declareStream(StreamType.ISL.toString(), fields);
        declarer.declareStream(StreamType.FLOW.toString(), fields);
        declarer.declareStream(StreamType.FEATURE_TOGGLES.toString(), fields);
        declarer.declareStream(StreamType.KILDA_CONFIG.toString(), fields);
        declarer.declareStream(StreamType.PATHS.toString(), fields);
        declarer.declareStream(StreamType.HISTORY.toString(), fields);

        declarer.declareStream(StreamType.VALIDATION.toString(),
                new Fields(SwitchValidationsBolt.FIELD_ID_REQUEST, SwitchValidationsBolt.FIELD_ID_CONTEXT));

        declarer.declareStream(StreamType.ERROR.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));

        declarer.declareStream(FlowValidationHubBolt.INCOME_STREAM, MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(SpeakerWorkerBolt.INCOME_STREAM, MessageTranslator.STREAM_FIELDS);
    }
}
