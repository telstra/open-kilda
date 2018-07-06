/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowPingRequest;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class InputRouter extends Abstract {
    public static final String BOLT_ID = ComponentId.INPUT_ROUTER.toString();

    public static final String FIELD_ID_PING_RESPONSE = "ping_response";
    public static final String FIELD_ID_PING_REQUEST = "ping_request";

    public static final Fields STREAM_SPEAKER_PING_RESPONSE_FIELDS = new Fields(
            FIELD_ID_PING_RESPONSE, FIELD_ID_CONTEXT);
    public static final String STREAM_SPEAKER_PING_RESPONSE_ID = "ping_response";

    public static final Fields STREAM_PING_REQUEST_FIELDS = new Fields(
            FIELD_ID_PING_REQUEST, FIELD_ID_CONTEXT);
    public static final String STREAM_ON_DEMAND_REQUEST_ID = "ping_request";

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        Message message = pullInput(input);

        if (message instanceof InfoMessage) {
            routeInfoMessage(input, (InfoMessage) message);
        } else if (message instanceof CommandMessage) {
            routeCommandMessage(input, (CommandMessage) message);
        } else {
            unhandledInput(input);
        }
    }

    private void routeInfoMessage(Tuple input, InfoMessage message) throws PipelineException {
        InfoData data = message.getData();
        if (data instanceof PingResponse) {
            // Speaker response on ping command
            emit(input, new Values(data), STREAM_SPEAKER_PING_RESPONSE_ID);
        } else {
            unhandledInput(input);
        }
    }

    private void routeCommandMessage(Tuple input, CommandMessage message) throws PipelineException {
        final CommandData data = message.getData();
        if (data instanceof FlowPingRequest) {
            emit(input, new Values(data), STREAM_ON_DEMAND_REQUEST_ID);
        } else {
            unhandledInput(input);
        }
    }

    private void emit(Tuple input, Values output, String stream) throws PipelineException {
        output.add(pullContext(input));
        getOutput().emit(stream, input, output);
    }

    private Message pullInput(Tuple input) throws PipelineException {
        return pullValue(input, InputDecoder.FIELD_ID_INPUT, Message.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_SPEAKER_PING_RESPONSE_ID, STREAM_SPEAKER_PING_RESPONSE_FIELDS);
        outputManager.declareStream(STREAM_ON_DEMAND_REQUEST_ID, STREAM_PING_REQUEST_FIELDS);
    }
}
