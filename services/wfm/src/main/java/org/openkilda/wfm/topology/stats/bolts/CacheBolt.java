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
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.Commands;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.FieldsNames;
import org.openkilda.wfm.topology.stats.model.FlowPathReference;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;
import org.openkilda.wfm.topology.stats.model.StatsFlowBatch;
import org.openkilda.wfm.topology.stats.model.StatsFlowEntry;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class CacheBolt extends AbstractBolt {

    public static final String FIELD_ID_PAYLOAD = "payload";
    public static final String METER_CACHE_FIELD = "meter_cache";

    public static final String STREAM_FLOW_STATS_ID = FLOW_STATS.name();
    public static final Fields STREAM_FLOW_STATS_FIELDS = new Fields(FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public static final String STREAM_METER_STATS_ID = METER_STATS.name();
    public static final Fields STREAM_METER_STATS_FIELDS = new Fields(STATS_FIELD, METER_CACHE_FIELD, FIELD_ID_CONTEXT);

    /**
     * Path computation instance.
     */
    private final PersistenceManager persistenceManager;

    /**
     * Cookie to flow and meter to flow maps.
     */
    @VisibleForTesting
    @Getter(AccessLevel.PACKAGE)
    Map<FlowPathReference, CacheFlowEntry> flowsMetadataCache = new HashMap<>();

    private Map<MeterCacheKey, CacheFlowEntry> switchAndMeterToFlow = new HashMap<>();

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    private void initFlowCache(FlowRepository flowRepository) {
        try {
            flowRepository.findAll().stream()
                    .flatMap(this::extractAllFlowPaths)
                    .forEach(path -> {
                        CacheFlowEntry entry = new CacheFlowEntry(
                                path.getFlow().getFlowId(),
                                path.getSrcSwitch().getSwitchId(), path.getDestSwitch().getSwitchId(),
                                path.getCookie().getValue());

                        FlowPathReference reference = new FlowPathReference(path.getCookie());
                        flowsMetadataCache.put(reference, entry);
                        if (path.getMeterId() != null) {
                            switchAndMeterToFlow.put(
                                    new MeterCacheKey(
                                            path.getSrcSwitch().getSwitchId(), path.getMeterId().getValue()), entry);
                        } else {
                            log.warn("Flow {} has no meter ID", path.getFlow().getFlowId());
                        }
                    });
            log.debug(
                    "cookieToFlow cache: {}, switchAndMeterToFlow cache: {}", flowsMetadataCache, switchAndMeterToFlow);
            log.info("Stats Cache: Initialized");
        } catch (Exception ex) {
            log.error("Error on initFlowCache", ex);
        }
    }

    private Stream<FlowPath> extractAllFlowPaths(Flow flow) {
        return Stream.concat(
                Stream.of(flow.getForwardPath(), flow.getProtectedForwardPath()).filter(Objects::nonNull).peek(p -> {
                    p.setSrcSwitch(flow.getSrcSwitch());
                    p.setDestSwitch(flow.getDestSwitch());
                }),
                Stream.of(flow.getReversePath(), flow.getProtectedReversePath()).filter(Objects::nonNull).peek(p -> {
                    p.setSrcSwitch(flow.getDestSwitch());
                    p.setDestSwitch(flow.getSrcSwitch());
                })
        );
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

        if (data instanceof FlowStatsData) {
            handleFlowStats((FlowStatsData) data);
        } else if (data instanceof MeterStatsData) {
            Map<MeterCacheKey, CacheFlowEntry> meterDataCache = createSwitchAndMeterToFlowCache((MeterStatsData) data);
            Values values = new Values(data, meterDataCache, getCommandContext());
            getOutput().emit(STREAM_METER_STATS_ID, tuple, values);
        } else {
            unhandledInput(tuple);
            return;
        }
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
                flowsMetadataCache.remove(new FlowPathReference(cookie));
                switchAndMeterToFlow.remove(new MeterCacheKey(switchId, meterId));
                break;
            default:
                log.error("invalid cache command: {}", command);
                break;
        }

        // FIXME(surabujin): I believe this bad idea to log entire cache here (it can be a lot of megabytes)
        log.debug("updated cookieToFlow: {}", flowsMetadataCache);
    }

    private void handleFlowStats(FlowStatsData data) {
        List<StatsFlowEntry> stats = new ArrayList<>();

        for (FlowStatsEntry entry : data.getStats()) {
            FlowPathReference reference = new FlowPathReference(entry.getCookie());
            StatsFlowEntry statsEntry = new StatsFlowEntry(flowsMetadataCache.get(reference), entry);
            log.debug("Flow stats entry with added(if any) flow data: {}", statsEntry);
            stats.add(statsEntry);
        }

        StatsFlowBatch statsBatch = new StatsFlowBatch(data.getSwitchId(), stats);
        emit(STREAM_FLOW_STATS_ID, getCurrentTuple(), makeFlowStatsTuple(statsBatch));
    }

    @VisibleForTesting
    Map<MeterCacheKey, CacheFlowEntry> createSwitchAndMeterToFlowCache(MeterStatsData data) {
        Map<MeterCacheKey, CacheFlowEntry> cache = new HashMap<>();

        for (MeterStatsEntry entry : data.getStats()) {
            MeterCacheKey key = new MeterCacheKey(data.getSwitchId(), entry.getMeterId());
            if (switchAndMeterToFlow.containsKey(key)) {
                CacheFlowEntry cacheEntry = switchAndMeterToFlow.get(key);
                log.debug("Locate meter cache entry: {} => {}", key, cacheEntry);
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
        outputFieldsDeclarer.declareStream(STREAM_FLOW_STATS_ID, STREAM_FLOW_STATS_FIELDS);
        outputFieldsDeclarer.declareStream(STREAM_METER_STATS_ID, STREAM_METER_STATS_FIELDS);
    }

    private void updateCookieFlowCache(
            Long rawCookie, String flowId, SwitchId switchId, MeasurePoint measurePoint) {
        FlowPathReference reference = new FlowPathReference(rawCookie);
        CacheFlowEntry current = flowsMetadataCache.getOrDefault(reference, new CacheFlowEntry(flowId, rawCookie));
        CacheFlowEntry replacement = current.replaceSwitch(switchId, measurePoint);
        flowsMetadataCache.put(reference, replacement);
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

    private Values makeFlowStatsTuple(StatsFlowBatch batch) {
        return new Values(batch, getCommandContext());
    }
}
