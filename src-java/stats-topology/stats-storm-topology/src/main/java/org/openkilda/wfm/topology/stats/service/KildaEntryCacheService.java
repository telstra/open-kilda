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
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.ONE_SWITCH;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.TRANSIT;

import org.openkilda.messaging.info.stats.BaseFlowPathInfo;
import org.openkilda.messaging.info.stats.BaseYFlowStatsInfo;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveYFlowStatsInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateYFlowStatsInfo;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.CookieCacheKey;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptorHandler;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;
import org.openkilda.wfm.topology.stats.model.SwitchFlowStats;
import org.openkilda.wfm.topology.stats.model.SwitchMeterStats;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class KildaEntryCacheService {
    private final FlowRepository commonFlowRepository;
    private final YFlowRepository yFlowRepository;

    private final KildaEntryCacheCarrier carrier;

    /**
     * Cookie to flow and meter to flow maps.
     */
    private final Map<CookieCacheKey, KildaEntryDescriptor> cookieToFlow = new HashMap<>();
    private final Map<MeterCacheKey, KildaEntryDescriptor> switchAndMeterToFlow = new HashMap<>();

    public KildaEntryCacheService(PersistenceManager persistenceManager, KildaEntryCacheCarrier carrier) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.commonFlowRepository = repositoryFactory.createFlowRepository();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
        this.carrier = carrier;
    }

    /**
     * Process the provided {@link FlowStatsData} by completing it with cached data and forwarding it.
     *
     * @param data the data to process.
     */
    public void completeAndForwardFlowStats(FlowStatsData data) {
        SwitchFlowStats stats = new SwitchFlowStats(data.getSwitchId());
        for (FlowStatsEntry entry : data.getStats()) {
            CookieCacheKey key = new CookieCacheKey(data.getSwitchId(), entry.getCookie());
            stats.add(entry, cookieToFlow.get(key));
        }
        carrier.emitFlowStats(stats);
    }

    /**
     * Process the provided {@link MeterStatsData} by completing it with cached data and forwarding it.
     *
     * @param data the data to process.
     */
    public void completeAndForwardMeterStats(MeterStatsData data) {
        SwitchMeterStats stats = new SwitchMeterStats(data.getSwitchId());
        for (MeterStatsEntry entry : data.getStats()) {
            MeterCacheKey key = new MeterCacheKey(data.getSwitchId(), entry.getMeterId());
            stats.add(entry, switchAndMeterToFlow.get(key));
        }
        carrier.emitMeterStats(stats);
    }

    /**
     * Update the cache with the provided path info.
     *
     * @param updatePathInfo the path info to apply.
     */
    public void addOrUpdateCache(UpdateFlowPathInfo updatePathInfo) {
        updateCache(new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow), updatePathInfo);
    }

    public void addOrUpdateCache(UpdateYFlowStatsInfo yFlowStatsInfo) {
        updateCache(new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow), yFlowStatsInfo);
    }

    /**
     * Removed the provided path info from the cache.
     *
     * @param removePathInfo the path info to apply.
     */
    public void removeCached(RemoveFlowPathInfo removePathInfo) {
        updateCache(new CacheRemoveHandler(cookieToFlow, switchAndMeterToFlow), removePathInfo);
    }

    public void removeCached(RemoveYFlowStatsInfo yFlowStatsInfo) {
        updateCache(new CacheRemoveHandler(cookieToFlow, switchAndMeterToFlow), yFlowStatsInfo);
    }

    /**
     * Refresh the cache by rereading the flow data from the persistence.
     */
    public void refreshCache() {
        refreshCommonFlowsCache();
        refreshYFlowsCache();
        log.debug("cookieToFlow cache: {}, switchAndMeterToFlow cache: {}", cookieToFlow, switchAndMeterToFlow);
    }

    private void updateCache(KildaEntryDescriptorHandler cacheHandler, BaseFlowPathInfo pathInfo) {
        updateCache(
                cacheHandler, pathInfo.getFlowId(), pathInfo.getYFlowId(), pathInfo.getCookie(), pathInfo.getMeterId(),
                pathInfo.getPathNodes());
    }

    private void updateCache(KildaEntryDescriptorHandler cacheHandler, BaseYFlowStatsInfo yFlowStatsInfo) {
        updateCache(
                cacheHandler, yFlowStatsInfo.getYFlowId(), yFlowStatsInfo.getSharedEndpointResources(),
                MeasurePoint.Y_FLOW_SHARED);
        updateCache(
                cacheHandler, yFlowStatsInfo.getYFlowId(), yFlowStatsInfo.getYPointResources(),
                MeasurePoint.Y_FLOW_Y_POINT);
        if (yFlowStatsInfo.getProtectedYPointResources() != null) {
            updateCache(
                    cacheHandler, yFlowStatsInfo.getYFlowId(), yFlowStatsInfo.getProtectedYPointResources(),
                    MeasurePoint.Y_FLOW_Y_POINT);
        }
    }

    private void updateCache(
            KildaEntryDescriptorHandler cacheHandler, String flowId, String yFlowId, FlowSegmentCookie cookie,
            MeterId meterId, List<PathNodePayload> pathNodes) {
        if (pathNodes.isEmpty()) {
            throw new IllegalArgumentException("The path can't be empty");
        }

        processTransitCookies(cacheHandler, flowId, yFlowId, cookie, pathNodes);

        SwitchId srcSwitchId = pathNodes.get(0).getSwitchId();
        SwitchId dstSwitchId = pathNodes.get(pathNodes.size() - 1).getSwitchId();

        boolean isOneSwitchFlow = srcSwitchId.equals(dstSwitchId);
        if (isOneSwitchFlow) {
            cacheHandler.handle(newPathEntry(srcSwitchId, ONE_SWITCH, flowId, yFlowId, cookie, meterId));
        } else {
            cacheHandler.handle(newPathEntry(srcSwitchId, INGRESS, flowId, yFlowId, cookie, meterId));
            cacheHandler.handle(newPathEntry(dstSwitchId, EGRESS, flowId, yFlowId, cookie));
        }
    }

    private void updateCache(
            KildaEntryDescriptorHandler cacheHandler, String yFlowId, YFlowEndpointResources resources,
            MeasurePoint measurePoint) {
        updateCache(cacheHandler, yFlowId, resources.getSwitchId(), resources.getMeterId(), measurePoint);
    }

    private void updateCache(
            KildaEntryDescriptorHandler cacheHandler, String yFlowId, SwitchId switchId, MeterId meterId,
            MeasurePoint measurePoint) {
        cacheHandler.handle(
                new YFlowDescriptor(switchId, measurePoint, yFlowId, meterId));
    }

    private void refreshCommonFlowsCache() {
        CacheAddUpdateHandler cacheHandler = new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow);
        commonFlowRepository.findAll().stream()
                .flatMap(flow -> flow.getPaths().stream())
                .filter(Objects::nonNull)
                .forEach(path -> {
                    String flowId = path.getFlowId();
                    updateCache(
                            cacheHandler, flowId, path.getFlow().getYFlowId(), path.getCookie(), path.getMeterId(),
                            FlowPathMapper.INSTANCE.mapToPathNodes(path));
                });
    }

    private void refreshYFlowsCache() {
        CacheAddUpdateHandler cacheHandler = new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow);
        for (YFlow entry : yFlowRepository.findAll()) {
            if (entry.getSharedEndpointMeterId() == null) {
                continue;
            }
            if (entry.getMeterId() == null) {
                continue;
            }

            updateCache(
                    cacheHandler, entry.getYFlowId(), entry.getSharedEndpoint().getSwitchId(),
                    entry.getSharedEndpointMeterId(), MeasurePoint.Y_FLOW_SHARED);
            updateCache(
                    cacheHandler, entry.getYFlowId(), entry.getYPoint(), entry.getMeterId(),
                    MeasurePoint.Y_FLOW_Y_POINT);
        }
    }

    private void processTransitCookies(
            KildaEntryDescriptorHandler cacheHandler, String flowId, String yFlowId, FlowSegmentCookie cookie,
            List<PathNodePayload> path) {
        // Skip the first and the last nodes as they're handled as INGRESS and EGRESS.
        for (int i = 1; i < path.size() - 1; i++) {
            SwitchId transitSrc = path.get(i).getSwitchId();
            cacheHandler.handle(newPathEntry(transitSrc, TRANSIT, flowId, yFlowId, cookie));
        }
    }

    private static KildaEntryDescriptor newPathEntry(
            SwitchId switchId, MeasurePoint measurePoint, String flowId, String yFlowId, FlowSegmentCookie cookie) {
        return newPathEntry(switchId, measurePoint, flowId, yFlowId, cookie, null);
    }

    private static KildaEntryDescriptor newPathEntry(
            SwitchId switchId, MeasurePoint measurePoint, String flowId, String yFlowId, FlowSegmentCookie cookie,
            MeterId meterId) {
        if (yFlowId != null) {
            return new YFlowSubDescriptor(switchId, measurePoint, yFlowId, flowId, cookie, meterId);
        }
        return new CommonFlowDescriptor(switchId, measurePoint, flowId, cookie, meterId);
    }
}
