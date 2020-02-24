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

package org.openkilda.wfm.topology.stats.bolts;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.utils.JsonKafkaTranslator;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;

public class SpeakerRequestDecoderBolt extends AbstractBolt {
    public static final String BOLT_ID = StatsComponentType.SPEAKER_REQUEST_DECODER.toString();

    public static final String FIELD_ID_KEY = JsonKafkaTranslator.FIELD_ID_KEY;
    public static final String FIELD_ID_PAYLOAD = JsonKafkaTranslator.FIELD_ID_PAYLOAD;

    public static final String STREAM_GENERIC_ID = "generic";
    public static final String STREAM_HUB_AND_SPOKE_ID = "hs";
    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String payload = pullValue(input, JsonKafkaTranslator.FIELD_ID_PAYLOAD, String.class);
        try {
            try {
                handleDecoded(Utils.MAPPER.readValue(payload, AbstractMessage.class));
            } catch (JsonMappingException e) {
                handleDecoded(Utils.MAPPER.readValue(payload, Message.class));
            }
        } catch (IOException e) {
            log.error("Unable to decode JSON message - error:{} json:\"{}\"", e, payload);
        }
    }

    private void handleDecoded(AbstractMessage message) throws PipelineException {
        CommandContext context = new CommandContext(message.getMessageContext().getCorrelationId());
        emit(STREAM_HUB_AND_SPOKE_ID, context, message);
    }

    private void handleDecoded(Message message) throws PipelineException {
        CommandContext context = new CommandContext(message);
        emit(STREAM_GENERIC_ID, context, message);
    }

    private void emit(String stream, CommandContext context, Object payload) throws PipelineException {
        Tuple input = getCurrentTuple();
        Values tuple = new Values(pullValue(input, FIELD_ID_KEY, String.class), payload, context);
        emit(stream, input, tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_GENERIC_ID, STREAM_FIELDS);
        streamManager.declareStream(STREAM_HUB_AND_SPOKE_ID, STREAM_FIELDS);
    }
}
