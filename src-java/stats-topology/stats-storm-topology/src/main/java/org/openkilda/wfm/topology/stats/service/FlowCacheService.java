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

package org.openkilda.wfm.topology.stats.service;

import static org.openkilda.wfm.topology.stats.model.MeasurePoint.EGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.INGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.INGRESS_ATTENDANT;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.ONE_SWITCH;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.TRANSIT;

import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.topology.stats.model.FlowCacheEntry;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class FlowCacheService {
    private final FlowRepository flowRepository;
    private final FlowCacheBoltCarrier carrier;

    /**
     * Cookie to flow and meter to flow maps.
     */
    private final Map<CookieCacheKey, FlowCacheEntry> cookieToFlow = new HashMap<>();
    private final Map<MeterCacheKey, FlowCacheEntry> switchAndMeterToFlow = new HashMap<>();

    public FlowCacheService(PersistenceManager persistenceManager, FlowCacheBoltCarrier carrier) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.carrier = carrier;
    }

    /**
     * Process the provided {@link FlowStatsData} by completing it with cached data and forwarding it.
     *
     * @param data the data to process.
     */
    public void completeAndForwardFlowStats(FlowStatsData data) {
        Map<Long, FlowCacheEntry> cache = new HashMap<>();

        for (FlowStatsEntry entry : data.getStats()) {
            CookieCacheKey key = new CookieCacheKey(data.getSwitchId(), entry.getCookie());
            if (cookieToFlow.containsKey(key)) {
                FlowCacheEntry cacheFlowEntry = cookieToFlow.get(key);
                cache.put(entry.getCookie(), cacheFlowEntry);
            }
        }

        carrier.emitFlowStats(data, cache);
    }

    /**
     * Process the provided {@link MeterStatsData} by completing it with cached data and forwarding it.
     *
     * @param data the data to process.
     */
    public void completeAndForwardMeterStats(MeterStatsData data) {
        Map<MeterCacheKey, FlowCacheEntry> cache = new HashMap<>();

        for (MeterStatsEntry entry : data.getStats()) {
            MeterCacheKey key = new MeterCacheKey(data.getSwitchId(), entry.getMeterId());
            if (switchAndMeterToFlow.containsKey(key)) {
                FlowCacheEntry cacheEntry = switchAndMeterToFlow.get(key);
                cache.put(key, cacheEntry);
            }
        }

        carrier.emitMeterStats(data, cache);
    }

    /**
     * Update the cache with the provided path info.
     *
     * @param updatePathInfo the path info to apply.
     */
    public void addOrUpdateCache(UpdateFlowPathInfo updatePathInfo) {
        List<PathNodePayload> pathNodes = updatePathInfo.getPathNodes();
        if (pathNodes.isEmpty()) {
            throw new IllegalArgumentException("The path can't be empty");
        }

        String flowId = updatePathInfo.getFlowId();
        long cookie = updatePathInfo.getCookie().getValue();
        processTransitCookies(flowId, cookie, pathNodes, cookieToFlow::put);

        SwitchId srcSwitchId = pathNodes.get(0).getSwitchId();
        SwitchId dstSwitchId = pathNodes.get(pathNodes.size() - 1).getSwitchId();
        processIngressAndEgressCookies(flowId, cookie, srcSwitchId, dstSwitchId, cookieToFlow::put);

        processMeter(flowId, cookie, updatePathInfo.getMeterId(), srcSwitchId, dstSwitchId, switchAndMeterToFlow::put);
    }

    /**
     * Removed the provided path info from the cache.
     *
     * @param removePathInfo the path info to apply.
     */
    public void removeCached(RemoveFlowPathInfo removePathInfo) {
        List<PathNodePayload> pathNodes = removePathInfo.getPathNodes();
        if (pathNodes.isEmpty()) {
            throw new IllegalArgumentException("The path can't be empty");
        }

        String flowId = removePathInfo.getFlowId();
        long cookie = removePathInfo.getCookie().getValue();
        processTransitCookies(flowId, cookie, pathNodes, (k, v) -> cookieToFlow.remove(k));

        SwitchId srcSwitchId = pathNodes.get(0).getSwitchId();
        SwitchId dstSwitchId = pathNodes.get(pathNodes.size() - 1).getSwitchId();
        processIngressAndEgressCookies(flowId, cookie, srcSwitchId, dstSwitchId, (k, v) -> cookieToFlow.remove(k));

        processMeter(flowId, cookie, removePathInfo.getMeterId(), srcSwitchId, dstSwitchId,
                (k, v) -> switchAndMeterToFlow.remove(k));
    }

    /**
     * Refresh the cache by rereading the flow data from the persistence.
     */
    public void refreshCache() {
        flowRepository.findAll().stream()
                .flatMap(flow -> flow.getPaths().stream())
                .filter(Objects::nonNull)
                .forEach(path -> {
                    String flowId = path.getFlowId();
                    long cookie = path.getCookie().getValue();
                    processTransitCookies(flowId, cookie, path, cookieToFlow::put);

                    SwitchId srcSwitchId = path.getSrcSwitchId();
                    SwitchId dstSwitchId = path.getDestSwitchId();
                    processIngressAndEgressCookies(flowId, cookie, srcSwitchId, dstSwitchId, cookieToFlow::put);

                    processMeter(flowId, cookie, path.getMeterId(), srcSwitchId, dstSwitchId,
                            switchAndMeterToFlow::put);
                });
        log.debug("cookieToFlow cache: {}, switchAndMeterToFlow cache: {}", cookieToFlow, switchAndMeterToFlow);
    }

    private void processTransitCookies(String flowId, long cookie, FlowPath path,
                                       CacheAction<CookieCacheKey, FlowCacheEntry> cacheAction) {
        // Skip the first segment as it's handled as INGRESS.
        List<PathSegment> segments = path.getSegments();
        for (int i = 1; i < segments.size(); i++) {
            SwitchId switchId = segments.get(i).getSrcSwitchId();
            cacheAction.accept(new CookieCacheKey(switchId, cookie), new FlowCacheEntry(flowId, cookie, TRANSIT));
        }
    }

    private void processTransitCookies(String flowId, long cookie, List<PathNodePayload> path,
                                       CacheAction<CookieCacheKey, FlowCacheEntry> cacheAction) {
        // Skip the first and the last nodes as they're handled as INGRESS and EGRESS.
        for (int i = 1; i < path.size() - 1; i++) {
            SwitchId transitSrc = path.get(i).getSwitchId();
            cacheAction.accept(new CookieCacheKey(transitSrc, cookie), new FlowCacheEntry(flowId, cookie, TRANSIT));
        }
    }

    private void processIngressAndEgressCookies(String flowId, long cookie, SwitchId srcSwitchId, SwitchId dstSwitchId,
                                                CacheAction<CookieCacheKey, FlowCacheEntry> cacheAction) {
        CookieCacheKey srcKey = new CookieCacheKey(srcSwitchId, cookie);
        boolean isOneSwitchFlow = srcSwitchId.equals(dstSwitchId);
        if (isOneSwitchFlow) {
            cacheAction.accept(srcKey, new FlowCacheEntry(flowId, cookie, ONE_SWITCH));
        } else {
            cacheAction.accept(srcKey, new FlowCacheEntry(flowId, cookie, INGRESS));
            makeFlowAttendantCookiesMapping(flowId, srcSwitchId, cookie).forEach(cacheAction::accept);

            cacheAction.accept(new CookieCacheKey(dstSwitchId, cookie), new FlowCacheEntry(flowId, cookie, EGRESS));
        }
    }

    private Map<CookieCacheKey, FlowCacheEntry> makeFlowAttendantCookiesMapping(
            String flowId, SwitchId ingressSwitchId, long cookie) {
        long server42IngressCookie = new FlowSegmentCookie(cookie).toBuilder()
                .type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build().getValue();
        return Collections.singletonMap(
                new CookieCacheKey(ingressSwitchId, server42IngressCookie),
                new FlowCacheEntry(flowId, server42IngressCookie, INGRESS_ATTENDANT));
    }

    private void processMeter(String flowId, long cookie, MeterId meterId, SwitchId srcSwitchId, SwitchId dstSwitchId,
                              CacheAction<MeterCacheKey, FlowCacheEntry> cacheAction) {
        if (meterId != null) {
            boolean isOneSwitchFlow = srcSwitchId.equals(dstSwitchId);
            MeasurePoint measurePoint = isOneSwitchFlow ? ONE_SWITCH : INGRESS;
            cacheAction.accept(
                    new MeterCacheKey(srcSwitchId, meterId.getValue()),
                    new FlowCacheEntry(flowId, cookie, measurePoint));
        } else {
            log.warn("Flow {} has no meter ID", flowId);
        }
    }

    private interface CacheAction<K, V> {
        void accept(K k, V v);
    }
}
