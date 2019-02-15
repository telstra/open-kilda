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

import static org.openkilda.messaging.Utils.TIMESTAMP;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_CACHE_FILTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_OFS_BOLT;
import static org.openkilda.wfm.topology.stats.StatsStreamType.FLOW_STATS;
import static org.openkilda.wfm.topology.stats.StatsStreamType.METER_STATS;
import static org.openkilda.wfm.topology.stats.StatsTopology.STATS_FIELD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.MeasurePoint;
import org.openkilda.wfm.topology.stats.MeterCacheKey;
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
import java.util.stream.Stream;

public class CacheBolt extends AbstractBolt {

    public static final String COOKIE_CACHE_FIELD = "cookie_cache";
    public static final String METER_CACHE_FIELD = "meter_cache";

    public static final Fields statsWithCacheFields =
            new Fields(STATS_FIELD, TIMESTAMP, COOKIE_CACHE_FIELD, METER_CACHE_FIELD);
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
    private Map<Long, CacheFlowEntry> cookieToFlow = new HashMap<>();
    private Map<MeterCacheKey, CacheFlowEntry> switchAndMeterToFlow = new HashMap<>();

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    private void initFlowCache(FlowPairRepository flowPairRepository) {
        try {
            flowPairRepository.findAll().stream()
                    .flatMap(flowPair -> Stream.of(flowPair.getForward(), flowPair.getReverse()))
                    .forEach(flow -> {
                        CacheFlowEntry entry = new CacheFlowEntry(
                                flow.getFlowId(),
                                flow.getSrcSwitch().getSwitchId().toOtsdFormat(),
                                flow.getDestSwitch().getSwitchId().toOtsdFormat(),
                                flow.getCookie());

                        cookieToFlow.put(flow.getCookie(), entry);
                        if (flow.getMeterId() != null) {
                            switchAndMeterToFlow.put(
                                    new MeterCacheKey(
                                            flow.getSrcSwitch().getSwitchId(), new Long(flow.getMeterId())), entry);
                        } else {
                            log.warn("Flow {} has no meter ID", flow.getFlowId());
                        }
                    });
            logger.debug("cookieToFlow cache: {}, switchAndMeterToFlow cache: {}", cookieToFlow, switchAndMeterToFlow);
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
        initFlowCache(repositoryFactory.createFlowPairRepository());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        StatsComponentType componentId = StatsComponentType.valueOf(tuple.getSourceComponent());

        if (componentId == STATS_CACHE_FILTER_BOLT) {
            handleUpdateCache(tuple);
        } else if (componentId == STATS_OFS_BOLT) {
            handleGetDataFromCache(tuple);
        }
    }

    private void handleGetDataFromCache(Tuple tuple) throws PipelineException {
        InfoData data = pullValue(tuple, STATS_FIELD, InfoData.class);
        Map<Long, CacheFlowEntry> cookieDataCache = null;
        Map<MeterCacheKey, CacheFlowEntry> meterDataCache = null;
        String streamId;

        if (data instanceof FlowStatsData) {
            streamId = FLOW_STATS.name();
            cookieDataCache = createCookieToFlowCache((FlowStatsData) data);
            logger.debug("execute:cookieDataCache: {}", cookieDataCache);
        } else if (data instanceof MeterStatsData) {
            streamId = METER_STATS.name();
            meterDataCache = createSwitchAndMeterToFlowCache((MeterStatsData) data);
            logger.debug("execute:meterDataCache: {}", meterDataCache);
        } else {
            unhandledInput(tuple);
            return;
        }

        Values values = new Values(data, tuple.getLongByField(TIMESTAMP), cookieDataCache, meterDataCache);
        getOutput().emit(streamId, tuple, values);
    }

    private void handleUpdateCache(Tuple tuple) {
        Long cookie = tuple.getLongByField(FieldsNames.COOKIE.name());
        Long meterId = tuple.getLongByField(FieldsNames.METER.name());
        String flow = tuple.getStringByField(FieldsNames.FLOW.name());
        SwitchId switchId = new SwitchId(tuple.getValueByField(FieldsNames.SWITCH.name()).toString());

        Commands command = (Commands) tuple.getValueByField(FieldsNames.COMMAND.name());
        MeasurePoint measurePoint = (MeasurePoint) tuple.getValueByField(FieldsNames.MEASURE_POINT.name());

        switch (command) {
            case UPDATE:
                updateCookieFlowCache(cookie, flow, switchId, measurePoint);
                updateSwitchMeterFlowCache(cookie, meterId, flow, switchId);
                break;
            case REMOVE:
                cookieToFlow.remove(cookie);
                switchAndMeterToFlow.remove(new MeterCacheKey(switchId, meterId));
                break;
            default:
                logger.error("invalid command");
                break;
        }

        logger.debug("updated cookieToFlow: {}", cookieToFlow);
    }

    private Map<Long, CacheFlowEntry> createCookieToFlowCache(FlowStatsData data) {
        Map<Long, CacheFlowEntry> cache = new HashMap<>();

        for (FlowStatsEntry entry : data.getStats()) {
            if (cookieToFlow.containsKey(entry.getCookie())) {
                CacheFlowEntry cacheFlowEntry = cookieToFlow.get(entry.getCookie());
                cache.put(entry.getCookie(), cacheFlowEntry);
            }
        }
        return cache;
    }

    private Map<MeterCacheKey, CacheFlowEntry> createSwitchAndMeterToFlowCache(MeterStatsData data) {
        Map<MeterCacheKey, CacheFlowEntry> cache = new HashMap<>();

        for (MeterStatsEntry entry : data.getStats()) {
            MeterCacheKey key = new MeterCacheKey(data.getSwitchId(), entry.getMeterId());
            if (switchAndMeterToFlow.containsKey(key)) {
                CacheFlowEntry cacheEntry = switchAndMeterToFlow.get(key);
                cache.put(key, cacheEntry);
            }
        }
        return cache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(FLOW_STATS.name(), statsWithCacheFields);
        outputFieldsDeclarer.declareStream(METER_STATS.name(), statsWithCacheFields);
    }

    private void updateCookieFlowCache(
            Long cookie, String flowId, SwitchId switchId, MeasurePoint measurePoint) {
        CacheFlowEntry current = cookieToFlow.getOrDefault(cookie, new CacheFlowEntry(flowId, cookie));
        CacheFlowEntry replacement = current.replaceSwitch(switchId.toOtsdFormat(), measurePoint);
        cookieToFlow.put(cookie, replacement);

    }

    private void updateSwitchMeterFlowCache(Long cookie, Long meterId, String flowId, SwitchId switchId) {
        MeterCacheKey key = new MeterCacheKey(switchId, meterId);
        CacheFlowEntry current = switchAndMeterToFlow.get(key);

        if (current == null) {
            switchAndMeterToFlow.put(key, new CacheFlowEntry(flowId, cookie));
        } else {
            switchAndMeterToFlow.put(key, current.replaceCookie(cookie));
        }
    }
}
