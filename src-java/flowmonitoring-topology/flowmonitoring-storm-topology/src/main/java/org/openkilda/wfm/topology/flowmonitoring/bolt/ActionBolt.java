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

package org.openkilda.wfm.topology.flowmonitoring.bolt;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.ACTION_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_UPDATE_STREAM_ID;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.info.flow.UpdateFlowInfo;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.ComponentId;
import org.openkilda.wfm.topology.flowmonitoring.service.ActionService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;

public class ActionBolt extends AbstractBolt implements FlowOperationsCarrier {

    public static final String FLOW_ID_FIELD = "flow-id";
    public static final String FLOW_DIRECTION_FIELD = "flow-direction";
    public static final String LATENCY_FIELD = "latency";
    public static final String FLOW_INFO_FIELD = "flow-info";


    private PersistenceManager persistenceManager;
    private Duration timeout;
    private float threshold;
    private transient ActionService actionService;

    public ActionBolt(PersistenceManager persistenceManager, Duration timeout, float threshold) {
        this.persistenceManager = persistenceManager;
        this.timeout = timeout;
        this.threshold = threshold;
    }

    @Override
    protected void init() {
        actionService = new ActionService(this, persistenceManager, Clock.systemUTC(), timeout, threshold);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (FLOW_UPDATE_STREAM_ID.name().equals(input.getSourceStreamId())) {
            UpdateFlowInfo flowInfo = pullValue(input, FLOW_INFO_FIELD, UpdateFlowInfo.class);
            actionService.updateFlowInfo(flowInfo);
            return;
        }

        if (ACTION_STREAM_ID.name().equals(input.getSourceStreamId())) {
            String flowId = pullValue(input, FLOW_ID_FIELD, String.class);
            FlowDirection direction = pullValue(input, FLOW_DIRECTION_FIELD, FlowDirection.class);
            Duration latency = pullValue(input, LATENCY_FIELD, Duration.class);

            actionService.processFlowLatencyMeasurement(flowId, direction, latency);
            return;
        }

        if (ComponentId.TICK_BOLT.name().equals(input.getSourceComponent())) {
            actionService.processTick();
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FLOW_ID_FIELD, FIELD_ID_CONTEXT));
    }

    @Override
    public void sendFlowSyncRequest(String flowId) {
        FlowRerouteRequest request = new FlowRerouteRequest(flowId, true, false, false, Collections.emptySet(),
                "Flow latency become healthy", false);
        emit(getCurrentTuple(), new Values(request, getCommandContext()));
    }

    @Override
    public void sendFlowRerouteRequest(String flowId) {
        FlowRerouteRequest request = new FlowRerouteRequest(flowId, false, false, false, Collections.emptySet(),
                "Flow latency become unhealthy", false);
        emit(getCurrentTuple(), new Values(request, getCommandContext()));
    }
}
