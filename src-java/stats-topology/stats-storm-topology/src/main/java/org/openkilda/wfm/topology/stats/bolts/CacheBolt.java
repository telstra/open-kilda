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

import static org.openkilda.wfm.topology.stats.MeasurePoint.EGRESS;
import static org.openkilda.wfm.topology.stats.MeasurePoint.INGRESS;
import static org.openkilda.wfm.topology.stats.MeasurePoint.INGRESS_ATTENDANT;
import static org.openkilda.wfm.topology.stats.MeasurePoint.ONE_SWITCH;
import static org.openkilda.wfm.topology.stats.MeasurePoint.TRANSIT;
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
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.stats.CacheFlowEntry;
import org.openkilda.wfm.topology.stats.CookieCacheKey;
import org.openkilda.wfm.topology.stats.MeasurePoint;
import org.openkilda.wfm.topology.stats.MeasurePointKey;
import org.openkilda.wfm.topology.stats.MeterCacheKey;
import org.openkilda.wfm.topology.stats.StatsComponentType;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.Commands;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt.FieldsNames;

import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class CacheBolt extends AbstractBolt {

    public static final String COOKIE_CACHE_FIELD = "cookie_cache";
    public static final String METER_CACHE_FIELD = "meter_cache";

    public static final Fields statsWithCacheFields =
            new Fields(STATS_FIELD, COOKIE_CACHE_FIELD, METER_CACHE_FIELD, FIELD_ID_CONTEXT);
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
    private final Map<CookieCacheKey, CacheFlowEntry> cookieToFlow = new HashMap<>();
    private final Map<MeterCacheKey, CacheFlowEntry> switchAndMeterToFlow = new HashMap<>();

    public CacheBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    private void initFlowCache(FlowRepository flowRepository) {
        try {
            flowRepository.findAll().stream()
                    .flatMap(this::extractAllFlowPaths)
                    .forEach(path -> {
                        long cookie = path.getCookie().getValue();
                        String flowId = path.getFlow().getFlowId();
                        SwitchId srcSwitchId = path.getSrcSwitchId();
                        SwitchId dstSwitchId = path.getDestSwitchId();

                        List<PathSegment> segments = path.getSegments();
                        for (int i = 0; i < segments.size() - 1; i++) {
                            PathSegment first = segments.get(i);
                            PathSegment second = segments.get(i + 1);
                            SwitchId switchId = second.getSrcSwitchId();
                            CookieCacheKey key = new CookieCacheKey(switchId, cookie);
                            cookieToFlow.put(key, new CacheFlowEntry(flowId, cookie, TRANSIT,
                                    MeasurePointKey.buildForSwitchRule(switchId, first.getDestPort(),
                                            second.getSrcPort())));
                        }

                        CookieCacheKey srcKey = new CookieCacheKey(srcSwitchId, cookie);

                        if (path.isOneSwitchFlow()) {
                            Flow flow = path.getFlow();
                            cookieToFlow.put(srcKey, new CacheFlowEntry(flowId, cookie, ONE_SWITCH,
                                    MeasurePointKey.buildForOneSwitchRule(srcSwitchId,
                                            flow.getSrcPort(), flow.getSrcVlan(), flow.getSrcInnerVlan(),
                                            flow.getDestPort(), flow.getDestVlan(), flow.getDestInnerVlan())));
                        } else {
                            Flow flow = path.getFlow();
                            MeasurePointKey ingressMeasurePointKey =
                                    MeasurePointKey.buildForIngressSwitchRule(srcSwitchId,
                                            flow.getSrcPort(), flow.getSrcVlan(), flow.getSrcInnerVlan(),
                                            segments.get(0).getSrcPort());
                            cookieToFlow.put(srcKey, new CacheFlowEntry(flowId, cookie, INGRESS,
                                    ingressMeasurePointKey));
                            cookieToFlow.putAll(makeFlowAttendantCookiesMapping(flowId, srcSwitchId, path.getCookie(),
                                    ingressMeasurePointKey));
                            MeasurePointKey egressMeasurePointKey =
                                    MeasurePointKey.buildForEgressSwitchRule(dstSwitchId,
                                            segments.get(segments.size() - 1).getDestPort(),
                                            flow.getDestPort(), flow.getDestVlan(), flow.getDestInnerVlan());
                            cookieToFlow.put(new CookieCacheKey(dstSwitchId, cookie),
                                    new CacheFlowEntry(flowId, cookie, EGRESS, egressMeasurePointKey));
                        }

                        if (path.getMeterId() != null) {
                            MeasurePoint measurePoint;
                            MeasurePointKey measurePointKey;
                            Flow flow = path.getFlow();
                            if (path.isOneSwitchFlow()) {
                                measurePoint = ONE_SWITCH;
                                measurePointKey = MeasurePointKey.buildForOneSwitchRule(srcSwitchId,
                                        flow.getSrcPort(), flow.getSrcVlan(), flow.getSrcInnerVlan(),
                                        flow.getDestPort(), flow.getDestVlan(), flow.getDestInnerVlan());
                            } else {
                                measurePoint = INGRESS;
                                measurePointKey = MeasurePointKey.buildForIngressSwitchRule(srcSwitchId,
                                        flow.getSrcPort(), flow.getSrcVlan(), flow.getSrcInnerVlan(),
                                        segments.get(0).getSrcPort());
                            }
                            switchAndMeterToFlow.put(
                                    new MeterCacheKey(srcSwitchId, path.getMeterId().getValue()),
                                    new CacheFlowEntry(flowId, cookie, measurePoint, measurePointKey));
                        } else {
                            log.warn("Flow {} has no meter ID", flowId);
                        }
                    });
            logger.debug("cookieToFlow cache: {}, switchAndMeterToFlow cache: {}", cookieToFlow, switchAndMeterToFlow);
            logger.info("Stats Cache: Initialized");
        } catch (Exception ex) {
            logger.error("Error on initFlowCache", ex);
        }
    }

    private Stream<FlowPath> extractAllFlowPaths(Flow flow) {
        return Stream.concat(
                Stream.of(flow.getForwardPath(), flow.getProtectedForwardPath()).filter(Objects::nonNull),
                Stream.of(flow.getReversePath(), flow.getProtectedReversePath()).filter(Objects::nonNull)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PersistenceContextRequired(requiresNew = true)
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

        Values values = new Values(data, cookieDataCache, meterDataCache, getCommandContext());
        getOutput().emit(streamId, tuple, values);
    }

    private void handleUpdateCache(Tuple tuple) {
        Long cookie = tuple.getLongByField(FieldsNames.COOKIE.name());
        Long meterId = tuple.getLongByField(FieldsNames.METER.name());
        String flow = tuple.getStringByField(FieldsNames.FLOW.name());
        SwitchId switchId = new SwitchId(tuple.getValueByField(FieldsNames.SWITCH.name()).toString());

        Commands command = (Commands) tuple.getValueByField(FieldsNames.COMMAND.name());
        MeasurePoint measurePointType = (MeasurePoint) tuple.getValueByField(FieldsNames.MEASURE_POINT_TYPE.name());
        MeasurePointKey measurePointKey = (MeasurePointKey) tuple.getValueByField(FieldsNames.MEASURE_POINT_KEY.name());

        switch (command) {
            case UPDATE:
                updateCookieFlowCache(cookie, flow, switchId, measurePointType, measurePointKey);
                updateSwitchMeterFlowCache(cookie, meterId, flow, switchId, measurePointType, measurePointKey);
                break;
            case REMOVE:
                removeCookieFlowCache(switchId, cookie, measurePointKey);
                removeSwitchMeterFromCache(switchId, meterId, measurePointKey);
                break;
            default:
                logger.error("invalid command");
                break;
        }

        logger.debug("updated cookieToFlow: {}", cookieToFlow);
    }

    @VisibleForTesting
    Map<Long, CacheFlowEntry> createCookieToFlowCache(FlowStatsData data) {
        Map<Long, CacheFlowEntry> cache = new HashMap<>();

        for (FlowStatsEntry entry : data.getStats()) {
            CookieCacheKey key = new CookieCacheKey(data.getSwitchId(), entry.getCookie());
            if (cookieToFlow.containsKey(key)) {
                CacheFlowEntry cacheFlowEntry = cookieToFlow.get(key);
                cache.put(entry.getCookie(), cacheFlowEntry);
            }
        }
        return cache;
    }

    @VisibleForTesting
    Map<MeterCacheKey, CacheFlowEntry> createSwitchAndMeterToFlowCache(MeterStatsData data) {
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

    private void updateCookieFlowCache(long cookie, String flowId, SwitchId switchId,
                                       MeasurePoint measurePoint, MeasurePointKey measurePointKey) {
        cookieToFlow.put(new CookieCacheKey(switchId, cookie),
                new CacheFlowEntry(flowId, cookie, measurePoint, measurePointKey));
        if (measurePoint == INGRESS) {
            cookieToFlow.putAll(makeFlowAttendantCookiesMapping(flowId, switchId, cookie, measurePointKey));
        }
    }

    private void updateSwitchMeterFlowCache(long cookie, Long meterId, String flowId, SwitchId switchId,
                                            MeasurePoint measurePoint, MeasurePointKey measurePointKey) {
        MeterCacheKey key = new MeterCacheKey(switchId, meterId);
        switchAndMeterToFlow.put(key,
                new CacheFlowEntry(flowId, cookie, measurePoint, measurePointKey));
    }

    private void removeCookieFlowCache(SwitchId switchId, long cookie, MeasurePointKey measurePointKey) {
        CookieCacheKey key = new CookieCacheKey(switchId, cookie);
        boolean keyFound = Optional.ofNullable(cookieToFlow.get(key))
                .filter(v -> measurePointKey == null || v.getMeasurePointKey().matches(measurePointKey))
                .isPresent();
        if (keyFound) {
            cookieToFlow.remove(key);
        }
        for (Long entry : makeAttendantFlowCookies(cookie)) {
            CookieCacheKey attFlowKey = new CookieCacheKey(switchId, entry);
            boolean attFlowKeyFound = Optional.ofNullable(cookieToFlow.get(attFlowKey))
                    .filter(v -> measurePointKey == null || v.getMeasurePointKey().matches(measurePointKey))
                    .isPresent();
            if (attFlowKeyFound) {
                cookieToFlow.remove(attFlowKey);
            }
        }
    }

    private void removeSwitchMeterFromCache(SwitchId switchId, Long meterId, MeasurePointKey measurePointKey) {
        MeterCacheKey key = new MeterCacheKey(switchId, meterId);
        boolean keyFound = Optional.ofNullable(switchAndMeterToFlow.get(key))
                .filter(v -> measurePointKey == null || v.getMeasurePointKey().matches(measurePointKey))
                .isPresent();
        if (keyFound) {
            switchAndMeterToFlow.remove(key);
        }
    }

    private Map<CookieCacheKey, CacheFlowEntry> makeFlowAttendantCookiesMapping(
            String flowId, SwitchId ingressSwitchId, long cookie, MeasurePointKey ingressMeasurePointKey) {
        return makeFlowAttendantCookiesMapping(flowId, ingressSwitchId, new FlowSegmentCookie(cookie),
                ingressMeasurePointKey);
    }

    private Map<CookieCacheKey, CacheFlowEntry> makeFlowAttendantCookiesMapping(
            String flowId, SwitchId ingressSwitchId, FlowSegmentCookie cookie, MeasurePointKey ingressMeasurePointKey) {
        Map<CookieCacheKey, CacheFlowEntry> results = new HashMap<>();

        long server42Cookie = cookie.toBuilder().type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build().getValue();
        results.put(
                new CookieCacheKey(ingressSwitchId, server42Cookie),
                new CacheFlowEntry(flowId, server42Cookie, INGRESS_ATTENDANT, ingressMeasurePointKey));

        return results;
    }

    private Set<Long> makeAttendantFlowCookies(long rawCookie) {
        Set<Long> results = new HashSet<>();

        FlowSegmentCookie cookie = new FlowSegmentCookie(rawCookie);
        results.add(cookie.toBuilder().type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build().getValue());

        return results;
    }
}
