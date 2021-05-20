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
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.UpdateFlowInfo;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.ComponentId;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;
import org.openkilda.wfm.topology.flowmonitoring.service.FlowCacheBoltCarrier;
import org.openkilda.wfm.topology.flowmonitoring.service.FlowCacheService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.time.Clock;
import java.util.List;

public class FlowCacheBolt extends AbstractBolt implements FlowCacheBoltCarrier {
    public static final String FLOW_ID_FIELD = "flow-id";
    public static final String FLOW_DIRECTION_FIELD = "flow-direction";
    public static final String FLOW_PATH_FIELD = "flow-path";
    public static final String LATENCY_FIELD = "latency";
    public static final String MAX_LATENCY_FIELD = "max-latency";
    public static final String MAX_LATENCY_TIER_2_FIELD = "max-latency-tier-2";
    public static final String FLOW_INFO_FIELD = "flow-info";

    private PersistenceManager persistenceManager;
    private long flowRttStatsExpirationTime;

    private transient FlowCacheService flowCacheService;

    public FlowCacheBolt(PersistenceManager persistenceManager, long flowRttStatsExpirationTime,
                         String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
        this.persistenceManager = persistenceManager;
        this.flowRttStatsExpirationTime = flowRttStatsExpirationTime;
    }

    @PersistenceContextRequired(requiresNew = true)
    protected void init() {
        flowCacheService = new FlowCacheService(persistenceManager, Clock.systemUTC(),
                flowRttStatsExpirationTime, this);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (active) {
            if (ComponentId.TICK_BOLT.name().equals(input.getSourceComponent())) {
                flowCacheService.processFlowLatencyCheck();
                return;
            }

            InfoData payload = pullValue(input, FIELD_ID_PAYLOAD, InfoMessage.class).getData();
            if (payload instanceof FlowRttStatsData) {
                FlowRttStatsData flowRttStatsData = (FlowRttStatsData) payload;
                flowCacheService.processFlowRttStatsData(flowRttStatsData);
            } else if (payload instanceof UpdateFlowInfo) {
                UpdateFlowInfo updateFlowInfo = (UpdateFlowInfo) payload;
                flowCacheService.updateFlowInfo(updateFlowInfo);
                emit(FLOW_UPDATE_STREAM_ID.name(), input, new Values(updateFlowInfo.getFlowId(), updateFlowInfo,
                        getCommandContext()));
            } else {
                unhandledInput(input);
            }
        }
    }

    @Override
    public void emitCalculateFlowLatencyRequest(String flowId, FlowDirection direction, List<Link> flowPath) {
        emit(getCurrentTuple(), new Values(flowId, direction, flowPath, getCommandContext()));
    }

    @Override
    public void emitCheckFlowLatencyRequest(String flowId, FlowDirection direction, long latency) {
        emit(ACTION_STREAM_ID.name(), getCurrentTuple(), new Values(flowId, direction, latency, getCommandContext()));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FLOW_ID_FIELD, FLOW_DIRECTION_FIELD,
                FLOW_PATH_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(ACTION_STREAM_ID.name(), new Fields(FLOW_ID_FIELD, FLOW_DIRECTION_FIELD,
                LATENCY_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(FLOW_UPDATE_STREAM_ID.name(), new Fields(FLOW_ID_FIELD, FLOW_INFO_FIELD,
                FIELD_ID_CONTEXT));
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
