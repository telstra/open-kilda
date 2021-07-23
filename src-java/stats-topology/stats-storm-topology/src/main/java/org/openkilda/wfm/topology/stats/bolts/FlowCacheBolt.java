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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.wfm.topology.stats.StatsTopology.STATS_FIELD;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.stats.model.FlowCacheEntry;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;
import org.openkilda.wfm.topology.stats.service.FlowCacheBoltCarrier;
import org.openkilda.wfm.topology.stats.service.FlowCacheService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class FlowCacheBolt extends AbstractBolt implements FlowCacheBoltCarrier {
    public static final String ZOOKEEPER_STREAM = ZkStreams.ZK.toString();

    public static final String FLOW_STATS_STREAM = "FLOW_STATS";
    public static final String METER_STATS_STREAM = "METER_STATS";

    public static final String STATS_CACHED_COOKIE_FIELD = "cached_cookie";
    public static final String STATS_CACHED_METER_FIELD = "meter_cache";
    public static final Fields STATS_WITH_CACHED_FIELDS =
            new Fields(STATS_FIELD, STATS_CACHED_COOKIE_FIELD, STATS_CACHED_METER_FIELD, FIELD_ID_CONTEXT);

    private final PersistenceManager persistenceManager;
    private transient FlowCacheService flowCacheService;

    public FlowCacheBolt(PersistenceManager persistenceManager, String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
        this.persistenceManager = persistenceManager;
    }

    @PersistenceContextRequired(requiresNew = true)
    protected void init() {
        flowCacheService = new FlowCacheService(persistenceManager, this);
        try {
            flowCacheService.refreshCache();
        } catch (Exception ex) {
            log.error("Error on сache initialization", ex);
        }
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (active) {
            if (input.contains(STATS_FIELD)) {
                InfoData data = pullValue(input, STATS_FIELD, InfoData.class);
                if (data instanceof FlowStatsData) {
                    flowCacheService.completeAndForwardFlowStats((FlowStatsData) data);
                } else if (data instanceof MeterStatsData) {
                    flowCacheService.completeAndForwardMeterStats((MeterStatsData) data);
                } else {
                    unhandledInput(input);
                }
            } else if (input.contains(FIELD_ID_PAYLOAD)) {
                InfoData data = pullValue(input, FIELD_ID_PAYLOAD, InfoMessage.class).getData();
                if (data instanceof UpdateFlowPathInfo) {
                    flowCacheService.addOrUpdateCache((UpdateFlowPathInfo) data);
                } else if (data instanceof RemoveFlowPathInfo) {
                    flowCacheService.removeCached((RemoveFlowPathInfo) data);
                } else {
                    unhandledInput(input);
                }
            } else {
                unhandledInput(input);
            }
        }
    }

    @Override
    public void emitFlowStats(FlowStatsData data, Map<Long, FlowCacheEntry> cookieDataCache) {
        Values values = new Values(data, cookieDataCache, null);
        emitWithContext(FLOW_STATS_STREAM, getCurrentTuple(), values);
    }

    @Override
    public void emitMeterStats(MeterStatsData data, Map<MeterCacheKey, FlowCacheEntry> meterDataCache) {
        Values values = new Values(data, null, meterDataCache);
        emitWithContext(METER_STATS_STREAM, getCurrentTuple(), values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(FLOW_STATS_STREAM, STATS_WITH_CACHED_FIELDS);
        declarer.declareStream(METER_STATS_STREAM, STATS_WITH_CACHED_FIELDS);
        declarer.declareStream(ZOOKEEPER_STREAM,
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
