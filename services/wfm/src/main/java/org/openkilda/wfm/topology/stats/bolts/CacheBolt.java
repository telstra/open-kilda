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

package org.openkilda.wfm.topology.stats.bolts;

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_CACHE_FILTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_OFS_BOLT;
import static org.openkilda.wfm.topology.stats.StatsStreamType.FLOW_STATS;
import static org.openkilda.wfm.topology.stats.StatsStreamType.METER_STATS;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.FlowStatsReply;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsReply;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.stats.FlowByCookieCacheEntry;
import org.openkilda.wfm.topology.stats.FlowByMeterCacheEntry;
import org.openkilda.wfm.topology.stats.MeasurePoint;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.Commands;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.FieldsNames;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CacheBolt extends AbstractBolt {

    public static final String COOKIE_CACHE_FIELD = "cookie_cache";
    public static final String METER_CACHE_FIELD = "meter_cache";

    public static final Fields fieldsMessageFlowStats =
            new Fields(
                    MESSAGE_FIELD,
                    COOKIE_CACHE_FIELD,
                    METER_CACHE_FIELD);
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(
            CacheBolt.class);

    /**
     * Path computation instance.
     */
    private final PersistenceManager persistenceManager;

    /**
     * Cookie to flow and meter to flow maps.
     */
    private Map<Long, FlowByCookieCacheEntry> cookieToFlow = new HashMap<>();
    private Map<Long, FlowByMeterCacheEntry> meterToFlow = new HashMap<>();

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    private void initFlowCache(FlowRepository flowRepository) {
        try {
            flowRepository.findAll().forEach(
                    flow -> {
                        cookieToFlow.put(flow.getCookie(), new FlowByCookieCacheEntry(
                                flow.getFlowId(),
                                flow.getSrcSwitch().getSwitchId().toOtsdFormat(),
                                flow.getDestSwitch().getSwitchId().toOtsdFormat()));
                        meterToFlow.put((long) flow.getMeterId(),
                                new FlowByMeterCacheEntry(flow.getFlowId(), flow.getCookie()));
                    }
            );
            logger.debug("cookieToFlow cache: {}, meterToFlow cache: {}", cookieToFlow, meterToFlow);
            logger.info("Stats Cache: Initialized");
        } catch (Exception ex) {
            logger.error("Error on initFlowCache", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        initFlowCache(repositoryFactory.createFlowRepository());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple tuple) {
        StatsComponentType componentId = StatsComponentType.valueOf(tuple.getSourceComponent());

        if (componentId == STATS_CACHE_FILTER_BOLT) {

            Long cookie = tuple.getLongByField(FieldsNames.COOKIE.name());
            Long meterId = tuple.getLongByField(FieldsNames.METER.name());
            String flow = tuple.getStringByField(FieldsNames.FLOW.name());
            String sw = new SwitchId(tuple.getValueByField(FieldsNames.SWITCH.name()).toString()).toOtsdFormat();

            Commands command = (Commands) tuple.getValueByField(FieldsNames.COMMAND.name());
            MeasurePoint measurePoint = (MeasurePoint) tuple.getValueByField(FieldsNames.MEASURE_POINT.name());

            switch (command) {
                case UPDATE:
                    updateCacheEntry(cookie, meterId, flow, sw, measurePoint);
                    break;
                case REMOVE:
                    cookieToFlow.remove(cookie);
                    meterToFlow.remove(meterId);
                    break;
                default:
                    logger.error("invalid command");
                    break;
            }

            logger.debug("updated cookieToFlow: {}", cookieToFlow);
        } else if (componentId == STATS_OFS_BOLT) {
            InfoMessage message = (InfoMessage) tuple.getValueByField(MESSAGE_FIELD);
            Map<Long, FlowByCookieCacheEntry> cookieDataCache = null;
            Map<Long, FlowByMeterCacheEntry> meterDataCache = null;
            String streamId;

            if (message.getData() instanceof FlowStatsData) {
                streamId = FLOW_STATS.name();
                cookieDataCache = createCookieToFlowCache((FlowStatsData) message.getData());
                logger.debug("execute:cookieDataCache: {}", cookieDataCache);
            } else if (message.getData() instanceof MeterStatsData) {
                streamId = METER_STATS.name();
                meterDataCache = createMeterToFlowCache((MeterStatsData) message.getData());
                logger.debug("execute:meterDataCache: {}", meterDataCache);
            } else {
                unhandledInput(tuple);
                return;
            }

            Values values = new Values(message, cookieDataCache, meterDataCache);
            getOutput().emit(streamId, tuple, values);
        }
    }

    private Map<Long, FlowByCookieCacheEntry> createCookieToFlowCache(FlowStatsData data) {
        Map<Long, FlowByCookieCacheEntry> cache = new HashMap<>();

        for (FlowStatsReply reply : data.getStats()) {
            for (FlowStatsEntry entry : reply.getEntries()) {
                if (cookieToFlow.containsKey(entry.getCookie())) {
                    FlowByCookieCacheEntry flowByCookieCacheEntry = cookieToFlow.get(entry.getCookie());
                    cache.put(entry.getCookie(), flowByCookieCacheEntry);
                }
            }
        }
        return cache;
    }

    private Map<Long, FlowByMeterCacheEntry> createMeterToFlowCache(MeterStatsData data) {
        Map<Long, FlowByMeterCacheEntry> cache = new HashMap<>();

        for (MeterStatsReply reply : data.getStats()) {
            for (MeterStatsEntry entry : reply.getEntries()) {
                if (meterToFlow.containsKey(entry.getMeterId())) {
                    FlowByMeterCacheEntry cacheEntry = meterToFlow.get(entry.getMeterId());
                    cache.put(entry.getMeterId(), cacheEntry);
                }
            }
        }
        return cache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(FLOW_STATS.name(), fieldsMessageFlowStats);
        outputFieldsDeclarer.declareStream(METER_STATS.name(), fieldsMessageFlowStats);
    }

    private void updateCacheEntry(Long cookie, Long meterId, String flowId, String sw, MeasurePoint measurePoint) {
        FlowByCookieCacheEntry current = cookieToFlow.get(cookie);
        FlowByCookieCacheEntry replacement;
        if (current != null) {
            replacement = current.replace(sw, measurePoint);
        } else {
            replacement = new FlowByCookieCacheEntry(flowId).replace(sw, measurePoint);
        }
        cookieToFlow.put(cookie, replacement);

        if (meterId != null) {
            meterToFlow.put(meterId, new FlowByMeterCacheEntry(flowId, cookie));
        }
    }
}
