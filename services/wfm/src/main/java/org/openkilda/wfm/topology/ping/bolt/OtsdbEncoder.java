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
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.error.PipelineException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class OtsdbEncoder extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.OTSDB_ENCODER.toString();

    public static final String FIELD_ID_STATS_DATAPOINT = "stats";

    public static final Fields STREAM_FIELDS = new Fields(
            FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);

    @Override
    protected void handleInput(Tuple input) throws Exception {
        Datapoint datapoint = pullDatapoint(input);
        String json = encode(datapoint);

        Values output = new Values(null, json);
        getOutput().emit(input, output);
    }

    private String encode(Datapoint datapoint) throws JsonEncodeException {
        String encoded;
        try {
            encoded = Utils.MAPPER.writeValueAsString(datapoint);
        } catch (JsonProcessingException e) {
            throw new JsonEncodeException(datapoint, e);
        }
        return encoded;
    }

    private Datapoint pullDatapoint(Tuple input) throws PipelineException {
        return pullValue(input, FIELD_ID_STATS_DATAPOINT, Datapoint.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
