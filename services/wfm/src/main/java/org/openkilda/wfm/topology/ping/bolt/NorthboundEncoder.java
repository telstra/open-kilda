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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.error.PipelineException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class NorthboundEncoder extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.NORTHBOUND_ENCODER.toString();

    public static final String FIELD_ID_PAYLOAD = "payload";

    public static final Fields STREAM_FIELDS = new Fields(
            FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        InfoData payload = pullPayload(input);
        InfoMessage message = wrap(input, payload);
        String json = encode(message);

        Values output = new Values(null, json);
        getOutput().emit(input, output);
    }

    private InfoMessage wrap(Tuple input, InfoData payload) throws PipelineException {
        CommandContext commandContext = pullContext(input);
        return new InfoMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId());
    }

    private String encode(InfoMessage message) throws JsonEncodeException {
        String json;
        try {
            json = Utils.MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new JsonEncodeException(message, e);
        }
        return json;
    }

    private InfoData pullPayload(Tuple input) throws PipelineException {
        return pullValue(input, FIELD_ID_PAYLOAD, InfoData.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
