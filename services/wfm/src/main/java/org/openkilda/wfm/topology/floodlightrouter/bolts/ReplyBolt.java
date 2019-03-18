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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.AbstractTopology;

import lombok.extern.slf4j.Slf4j;
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
    protected void handleInput(Tuple input) throws AbstractException {
        Object key = input.getValueByField(AbstractTopology.KEY_FIELD);
        String message = input.getValueByField(AbstractTopology.MESSAGE_FIELD).toString();
        Values values = new Values(key, message);
        getOutput().emit(outputStream, input, values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(outputStream, new Fields(AbstractTopology.KEY_FIELD,
                AbstractTopology.MESSAGE_FIELD));
    }
}
