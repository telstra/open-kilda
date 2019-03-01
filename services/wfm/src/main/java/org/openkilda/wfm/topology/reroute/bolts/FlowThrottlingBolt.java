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

package org.openkilda.wfm.topology.reroute.bolts;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.service.ReroutesThrottling;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class FlowThrottlingBolt extends AbstractTickStatefulBolt<InMemoryKeyValueState<String, ReroutesThrottling>> {

    private static final String REROUTES_THROTTLING = "reroutes-throttling";

    private final long minDelay;
    private final long maxDelay;
    private int defaultFlowPriority;

    private transient ReroutesThrottling reroutesThrottling;

    public FlowThrottlingBolt(long minDelay, long maxDelay, int defaultFlowPriority) {
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.defaultFlowPriority = defaultFlowPriority;
    }

    @Override
    protected void doTick(Tuple tuple) {
        for (Map.Entry<String, FlowThrottlingData> entry: reroutesThrottling.getReroutes()) {
            String flowId = entry.getKey();
            FlowThrottlingData throttlingData = entry.getValue();
            FlowRerouteRequest request = new FlowRerouteRequest(flowId);
            outputCollector.emit(tuple, new Values(throttlingData.getCorrelationId(),
                    new CommandMessage(request, System.currentTimeMillis(), throttlingData.getCorrelationId())));
        }
        outputCollector.ack(tuple);
    }

    @Override
    protected void doWork(Tuple tuple) {
        FlowThrottlingData throttlingData =
                (FlowThrottlingData) tuple.getValueByField(RerouteBolt.THROTTLING_DATA_FIELD);
        reroutesThrottling.putRequest(tuple.getStringByField(RerouteBolt.FLOW_ID_FIELD), throttlingData);
        outputCollector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields(MessageTranslator.KEY_FIELD, MessageTranslator.FIELD_ID_PAYLOAD));
    }

    @Override
    public void initState(InMemoryKeyValueState<String, ReroutesThrottling> state) {
        reroutesThrottling = state.get(REROUTES_THROTTLING);
        if (reroutesThrottling == null) {
            reroutesThrottling = new ReroutesThrottling(minDelay, maxDelay, defaultFlowPriority);
            state.put(REROUTES_THROTTLING, reroutesThrottling);
        }
    }
}
