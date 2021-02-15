/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.bolts;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.ACTION_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.FLOW_DIRECTION_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.FLOW_ID_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.LATENCY_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.MAX_LATENCY_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolts.FlowCacheBolt.MAX_LATENCY_TIER_2_FIELD;

import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.flowmonitoring.service.ActionService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

public class ActionBolt extends AbstractBolt {

    private transient ActionService actionService;

    @Override
    protected void init() {
        actionService = new ActionService();
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (ACTION_STREAM_ID.name().equals(input.getSourceStreamId())) {
            String flowId = pullValue(input, FLOW_ID_FIELD, String.class);
            FlowDirection direction = pullValue(input, FLOW_DIRECTION_FIELD, FlowDirection.class);
            Long latency = pullValue(input, LATENCY_FIELD, Long.class);
            Long maxLatency = pullValue(input, MAX_LATENCY_FIELD, Long.class);
            Long maxLatencyTier2 = pullValue(input, MAX_LATENCY_TIER_2_FIELD, Long.class);

            actionService.checkFlowSla(flowId, direction, latency, maxLatency, maxLatencyTier2);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }
}
