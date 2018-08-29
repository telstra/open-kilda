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

package org.openkilda.wfm.topology.cache;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.cache.service.ReroutesThrottling;
import org.openkilda.wfm.topology.utils.AbstractTickStatefulBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FlowThrottlingBolt extends AbstractTickStatefulBolt<InMemoryKeyValueState<String, ReroutesThrottling>> {

    private static final Logger logger = LoggerFactory.getLogger(FlowThrottlingBolt.class);

    private static final String REROUTES_THROTTLING = "reroutesThrottling";

    private final long minDelay;

    private final long maxDelay;

    private ReroutesThrottling reroutesThrottling;

    public FlowThrottlingBolt(long minDelay, long maxDelay) {
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        super.prepare(conf, context, collector);
    }

    @Override
    protected void doTick(Tuple tuple) {
        for (Map.Entry<String, String> entry: reroutesThrottling.getReroutes().entrySet()) {
            String flowId = entry.getKey();
            String correlationId = entry.getValue();
            FlowRerouteRequest request = new FlowRerouteRequest(flowId);
            try {
                String json = Utils.MAPPER.writeValueAsString(new CommandMessage(
                        request, System.currentTimeMillis(), correlationId, Destination.WFM));
                Values values = new Values(json);
                _collector.emit(tuple, values);
            } catch (JsonProcessingException exception) {
                logger.error("Could not format flow reroute request by flow={}", flowId, exception);
            }
        }
        _collector.ack(tuple);
    }

    @Override
    protected void doWork(Tuple tuple) {
        reroutesThrottling.putRequest(tuple.getStringByField(CacheBolt.FLOW_ID_FIELD),
                tuple.getStringByField(CacheBolt.CORRELATION_ID_FIELD));
        _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(AbstractTopology.fieldMessage);
    }

    @Override
    public void initState(InMemoryKeyValueState<String, ReroutesThrottling> state) {
        reroutesThrottling = state.get(REROUTES_THROTTLING);
        if (reroutesThrottling == null) {
            reroutesThrottling = new ReroutesThrottling(minDelay, maxDelay);
            state.put(REROUTES_THROTTLING, reroutesThrottling);
        }
    }
}
