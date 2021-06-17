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
import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.STATS_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowSplitterBolt.INFO_DATA_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.IslDataSplitterBolt.ISL_KEY_FIELD;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.IslDataSplitterBolt.getIslKey;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.flow.UpdateFlowInfo;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.bolt.KafkaEncoder;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.ComponentId;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;
import org.openkilda.wfm.topology.flowmonitoring.service.CalculateFlowLatencyService;
import org.openkilda.wfm.topology.flowmonitoring.service.FlowCacheBoltCarrier;
import org.openkilda.wfm.topology.flowmonitoring.service.FlowCacheService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FlowCacheBolt extends AbstractBolt implements FlowCacheBoltCarrier {
    public static final String FLOW_ID_FIELD = "flow-id";
    public static final String FLOW_DIRECTION_FIELD = "flow-direction";
    public static final String REQUEST_ID_FIELD = "request-id";
    public static final String LINK_FIELD = "link";
    public static final String LATENCY_FIELD = "latency";
    public static final String FLOW_INFO_FIELD = "flow-info";

    private PersistenceManager persistenceManager;
    private Duration flowRttStatsExpirationTime;
    private MetricFormatter metricFormatter;

    private transient FlowCacheService flowCacheService;
    private transient CalculateFlowLatencyService calculateFlowLatencyService;

    public FlowCacheBolt(PersistenceManager persistenceManager, Duration flowRttStatsExpirationTime,
                         String flowStatsPrefix, String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
        this.persistenceManager = persistenceManager;
        this.flowRttStatsExpirationTime = flowRttStatsExpirationTime;
        this.metricFormatter = new MetricFormatter(flowStatsPrefix);
    }

    @PersistenceContextRequired(requiresNew = true)
    protected void init() {
        flowCacheService = new FlowCacheService(persistenceManager, Clock.systemUTC(),
                flowRttStatsExpirationTime, this);
        calculateFlowLatencyService = new CalculateFlowLatencyService(this);
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (active) {
            if (ComponentId.TICK_BOLT.name().equals(input.getSourceComponent())) {
                flowCacheService.processFlowLatencyCheck();
                return;
            }

            if (ComponentId.ISL_CACHE_BOLT.name().equals(input.getSourceComponent())) {
                String requestId = pullValue(input, REQUEST_ID_FIELD, String.class);
                Link link = pullValue(input, LINK_FIELD, Link.class);
                Duration latency = pullValue(input, LATENCY_FIELD, Duration.class);
                calculateFlowLatencyService.handleGetLinkLatencyResponse(requestId, link, latency);
                return;
            }

            InfoData payload = pullValue(input, INFO_DATA_FIELD, InfoData.class);
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
        calculateFlowLatencyService.handleCalculateFlowLatencyRequest(flowId, direction, flowPath);
    }

    @Override
    public void emitGetLinkLatencyRequest(String flowId, String requestId, Link link) {
        emit(getCurrentTuple(), new Values(requestId, flowId, getIslKey(link.getSrcSwitchId(), link.getSrcPort()),
                link, getCommandContext()));
    }

    @Override
    public void emitCheckFlowLatencyRequest(String flowId, FlowDirection direction, Duration latency) {
        emit(ACTION_STREAM_ID.name(), getCurrentTuple(), new Values(flowId, direction, latency, getCommandContext()));
    }

    @Override
    public void emitLatencyStats(String flowId, FlowDirection direction, Duration latency) {
        Map<String, String> tags = ImmutableMap.of(
                "flowid", flowId,
                "direction", direction.name().toLowerCase(),
                "origin", "flow-monitoring"
        );

        Datapoint datapoint = new Datapoint(metricFormatter.format("flow.rtt"),
                System.currentTimeMillis(), tags, latency.toNanos());
        try {
            List<Object> tsdbTuple = Collections.singletonList(Utils.MAPPER.writeValueAsString(datapoint));
            emit(STATS_STREAM_ID.name(), getCurrentTuple(), tsdbTuple);
        } catch (JsonProcessingException e) {
            log.error("Couldn't create OpenTSDB tuple for flow {} latency stats", flowId, e);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(REQUEST_ID_FIELD, FLOW_ID_FIELD, ISL_KEY_FIELD, LINK_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(ACTION_STREAM_ID.name(), new Fields(FLOW_ID_FIELD, FLOW_DIRECTION_FIELD,
                LATENCY_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(FLOW_UPDATE_STREAM_ID.name(), new Fields(FLOW_ID_FIELD, FLOW_INFO_FIELD,
                FIELD_ID_CONTEXT));
        declarer.declareStream(STATS_STREAM_ID.name(), new Fields(KafkaEncoder.FIELD_ID_PAYLOAD));
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
