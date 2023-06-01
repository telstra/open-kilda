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

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.bolt.producer.FlowPingContextProducer;
import org.openkilda.wfm.topology.ping.bolt.producer.HaFlowPingContextProducer;
import org.openkilda.wfm.topology.ping.bolt.producer.PingContextProducer;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PingProducer extends Abstract {
    public static final String BOLT_ID = ComponentId.PING_PRODUCER.toString();

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_PING, FIELD_ID_CONTEXT);

    private static final FlowPingContextProducer FLOW_PING_CONTEXT_PRODUCER = new FlowPingContextProducer();
    private static final HaFlowPingContextProducer HA_FLOW_PING_CONTEXT_PRODUCER = new HaFlowPingContextProducer();

    @Override
    protected void handleInput(Tuple input) throws Exception {
        PingContext pingContext = pullPingContext(input);

        PingContextProducer producer = pingContext.isHaFlow() ? HA_FLOW_PING_CONTEXT_PRODUCER
                : FLOW_PING_CONTEXT_PRODUCER;

        for (PingContext context : producer.produce(pingContext)) {
            emit(input, context);
        }
    }

    private void emit(Tuple input, PingContext pingContext) throws PipelineException {
        CommandContext commandContext = pullContext(input);
        Values output = new Values(pingContext, commandContext);
        getOutput().emit(input, output);
    }


    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
