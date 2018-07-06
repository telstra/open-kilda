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
import org.openkilda.messaging.Utils;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.JsonDecodeException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;

public class InputDecoder extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.INPUT_DECODER.toString();

    public static final String FIELD_ID_INPUT = "input";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_INPUT, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String json = pullPayload(input);
        Message message = decode(json);
        CommandContext commandContext = new CommandContext(message);

        Values output = new Values(message, commandContext);
        getOutput().emit(input, output);
    }

    private String pullPayload(Tuple input) throws PipelineException {
        return pullValue(input, KafkaRecordTranslator.FIELD_ID_PAYLOAD, String.class);
    }

    private Message decode(String json) throws JsonDecodeException {
        Message value;

        try {
            value = Utils.MAPPER.readValue(json, Message.class);
        } catch (IOException e) {
            throw new JsonDecodeException(Message.class, json, e);
        }
        return value;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
