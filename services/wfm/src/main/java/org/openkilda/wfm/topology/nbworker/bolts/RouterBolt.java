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
import org.openkilda.messaging.nbtopology.request.FlowsBaseRequest;
import org.openkilda.messaging.nbtopology.request.GetPathsRequest;
import org.openkilda.messaging.nbtopology.request.HistoryRequest;
import org.openkilda.messaging.nbtopology.request.KildaConfigurationBaseRequest;
import org.openkilda.messaging.nbtopology.request.LinksBaseRequest;
import org.openkilda.messaging.nbtopology.request.SwitchesBaseRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class RouterBolt extends AbstractBolt {

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

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
            getOutput().emit(StreamType.SWITCH.toString(), input, new Values(request, getCommandContext()));
        } else if (request instanceof LinksBaseRequest) {
            getOutput().emit(StreamType.ISL.toString(), input, new Values(request, getCommandContext()));
        } else if (request instanceof FlowsBaseRequest) {
            getOutput().emit(StreamType.FLOW.toString(), input, new Values(request, getCommandContext()));
        } else if (request instanceof FeatureTogglesBaseRequest) {
            getOutput().emit(StreamType.FEATURE_TOGGLES.toString(), input, new Values(request, getCommandContext()));
        } else if (request instanceof KildaConfigurationBaseRequest) {
            getOutput().emit(StreamType.KILDA_CONFIG.toString(), input, new Values(request, getCommandContext()));
        } else if (request instanceof GetPathsRequest) {
            getOutput().emit(StreamType.PATHS.toString(), input, new Values(request, getCommandContext()));
        } else if (request instanceof HistoryRequest) {
            getOutput().emit(StreamType.HISTORY.toString(), input, new Values(request, getCommandContext()));
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, InfoData data) {
        if (data instanceof SwitchFlowEntries) {
            getOutput().emit(StreamType.VALIDATION.toString(), input, new Values(data, getCommandContext()));
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, ErrorData data) {
        getOutput().emit(StreamType.ERROR.toString(), input, new Values(data, getCommandContext()));
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
    }
}
