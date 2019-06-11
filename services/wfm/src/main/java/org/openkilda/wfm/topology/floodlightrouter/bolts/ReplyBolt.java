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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class ReplyBolt extends AbstractBolt {

    private String outputStream;

    public ReplyBolt(String outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String key = input.getStringByField(AbstractTopology.KEY_FIELD);
        Object message = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, Object.class);
        Values values = new Values(key, message);
        getOutput().emit(outputStream, input, values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        Fields fields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                                   FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        outputFieldsDeclarer.declareStream(outputStream, fields);
    }
}
