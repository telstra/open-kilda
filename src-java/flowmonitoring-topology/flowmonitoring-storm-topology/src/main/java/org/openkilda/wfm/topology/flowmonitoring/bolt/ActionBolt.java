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

import static org.openkilda.wfm.share.bolt.KafkaEncoder.FIELD_ID_PAYLOAD;
import static org.openkilda.wfm.share.bolt.MonotonicClock.FIELD_ID_TICK_IDENTIFIER;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.ACTION_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_HS_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_REMOVE_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_STATS_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_UPDATE_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_DIRECTION_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_ID_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.LATENCY_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowSplitterBolt.COMMAND_DATA_FIELD;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowSyncRequest;
import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
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

    private final Duration timeout;
    private final float threshold;
    private final int shardCount;
    private int currentShardNumber;
    private transient ActionService actionService;

    public ActionBolt(
            PersistenceManager persistenceManager, Duration timeout, float threshold,
            String lifeCycleEventSourceComponent, int shardCount) {
        super(persistenceManager, lifeCycleEventSourceComponent);
        this.timeout = timeout;
        this.threshold = threshold;
        this.currentShardNumber = 0;
        this.shardCount = shardCount;
    }

    @Override
    protected void init() {
        super.init();
        actionService = new ActionService(this, persistenceManager, Clock.systemUTC(), timeout, threshold, shardCount);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (!active) {
            return;
        }
        if (FLOW_UPDATE_STREAM_ID.name().equals(input.getSourceStreamId())) {
            UpdateFlowCommand updateFlowCommand = pullValue(input, COMMAND_DATA_FIELD, UpdateFlowCommand.class);
            actionService.updateFlowInfo(updateFlowCommand);
            return;
        }
        if (FLOW_REMOVE_STREAM_ID.name().equals(input.getSourceStreamId())) {
            String flowId = pullValue(input, FLOW_ID_FIELD, String.class);
            actionService.removeFlowInfo(flowId);
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
            TickBolt.TickId tickId = pullValue(input, FIELD_ID_TICK_IDENTIFIER, TickBolt.TickId.class);
            if (TickBolt.TickId.SLA_CHECK.equals(tickId)) {
                actionService.processTick(currentShardNumber);
                currentShardNumber = (currentShardNumber + 1) % shardCount;
            }
        } else {
            unhandledInput(input);
        }
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        actionService.purge();
        return true;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
        declarer.declareStream(FLOW_HS_STREAM_ID.name(), new Fields(FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
        declarer.declareStream(FLOW_STATS_STREAM_ID.name(),
                new Fields(FLOW_ID_FIELD, FLOW_DIRECTION_FIELD, LATENCY_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }

    @Override
    public void sendFlowSyncRequest(String flowId) {
        FlowSyncRequest request = new FlowSyncRequest(flowId);
        emit(FLOW_HS_STREAM_ID.name(), getCurrentTuple(), new Values(request, getCommandContext()));
    }

    @Override
    public void sendFlowRerouteRequest(String flowId) {
        FlowRerouteRequest request = new FlowRerouteRequest(flowId, false, false, Collections.emptySet(),
                "Flow latency become unhealthy", false);
        emit(getCurrentTuple(), new Values(request, getCommandContext()));
    }

    @Override
    public void persistFlowStats(String flowId, String direction, long latency) {
        emit(FLOW_STATS_STREAM_ID.name(), getCurrentTuple(), new Values(flowId, direction, latency,
                getCommandContext()));
    }
}
