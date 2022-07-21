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

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveYFlowStatsInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateYFlowStatsInfo;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.stats.model.SwitchFlowStats;
import org.openkilda.wfm.topology.stats.model.SwitchMeterStats;
import org.openkilda.wfm.topology.stats.service.KildaEntryCacheCarrier;
import org.openkilda.wfm.topology.stats.service.KildaEntryCacheService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class CacheBolt extends AbstractBolt implements KildaEntryCacheCarrier {
    public static final String ZOOKEEPER_STREAM = ZkStreams.ZK.toString();

    public static final String FLOW_STATS_STREAM = "FLOW_STATS";
    public static final String METER_STATS_STREAM = "METER_STATS";

    public static final Fields STATS_STREAM_FIELDS = new Fields(STATS_FIELD, FIELD_ID_CONTEXT);

    private transient KildaEntryCacheService cacheService;

    public CacheBolt(PersistenceManager persistenceManager, String lifeCycleEventSourceComponent) {
        super(persistenceManager, lifeCycleEventSourceComponent);
    }

    @PersistenceContextRequired(requiresNew = true)
    protected void init() {
        cacheService = new KildaEntryCacheService(persistenceManager, this);
    }

    @Override
    protected boolean activateAndConfirm() {
        try {
            cacheService.activate();
        } catch (Exception ex) {
            log.error(String.format("Error on cache initialization: %s", ex.getMessage()), ex);
            return false;
        }
        return true;
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        return cacheService.deactivate();
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        if (active) {
            handleWhileActive(input);
        }
    }

    private void handleWhileActive(Tuple input) throws PipelineException {
        if (input.contains(STATS_FIELD)) {
            handleStatsData(input);
        } else if (input.contains(FIELD_ID_PAYLOAD)) {
            handleCacheUpdate(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleStatsData(Tuple input) throws PipelineException {
        InfoData data = pullValue(input, STATS_FIELD, InfoData.class);
        if (data instanceof FlowStatsData) {
            cacheService.completeAndForwardFlowStats((FlowStatsData) data);
        } else if (data instanceof MeterStatsData) {
            cacheService.completeAndForwardMeterStats((MeterStatsData) data);
        } else {
            unhandledInput(input);
        }
    }

    private void handleCacheUpdate(Tuple input) throws PipelineException {
        InfoData data = pullValue(input, FIELD_ID_PAYLOAD, InfoMessage.class).getData();
        if (data instanceof UpdateFlowPathInfo) {
            cacheService.addOrUpdateCache((UpdateFlowPathInfo) data);
        } else if (data instanceof RemoveFlowPathInfo) {
            cacheService.removeCached((RemoveFlowPathInfo) data);
        } else if (data instanceof UpdateYFlowStatsInfo) {
            cacheService.addOrUpdateCache((UpdateYFlowStatsInfo) data);
        } else if (data instanceof RemoveYFlowStatsInfo) {
            cacheService.removeCached((RemoveYFlowStatsInfo) data);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void emitFlowStats(SwitchFlowStats stats) {
        Values values = new Values(stats, getCommandContext());
        emit(FLOW_STATS_STREAM, getCurrentTuple(), values);
    }

    @Override
    public void emitMeterStats(SwitchMeterStats stats) {
        Values values = new Values(stats, getCommandContext());
        emit(METER_STATS_STREAM, getCurrentTuple(), values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(FLOW_STATS_STREAM, STATS_STREAM_FIELDS);
        declarer.declareStream(METER_STATS_STREAM, STATS_STREAM_FIELDS);
        declarer.declareStream(ZOOKEEPER_STREAM,
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
