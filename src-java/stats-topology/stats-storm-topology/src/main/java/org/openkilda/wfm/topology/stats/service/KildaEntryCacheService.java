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
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.HA_FLOW_Y_POINT;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.INGRESS;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.ONE_SWITCH;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.TRANSIT;
import static org.openkilda.wfm.topology.stats.model.MeasurePoint.Y_FLOW_SHARED;

import org.openkilda.messaging.info.stats.BaseFlowPathInfo;
import org.openkilda.messaging.info.stats.BaseYFlowStatsInfo;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.GroupStatsData;
import org.openkilda.messaging.info.stats.GroupStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveHaFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveYFlowStatsInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateHaFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateYFlowStatsInfo;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.stats.model.CommonFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.CommonHaFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.CookieCacheKey;
import org.openkilda.wfm.topology.stats.model.EndpointFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.GroupCacheKey;
import org.openkilda.wfm.topology.stats.model.HaFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptorHandler;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;
import org.openkilda.wfm.topology.stats.model.StatVlanDescriptor;
import org.openkilda.wfm.topology.stats.model.SwitchFlowStats;
import org.openkilda.wfm.topology.stats.model.SwitchGroupStats;
import org.openkilda.wfm.topology.stats.model.SwitchMeterStats;
import org.openkilda.wfm.topology.stats.model.YFlowDescriptor;
import org.openkilda.wfm.topology.stats.model.YFlowSubDescriptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class KildaEntryCacheService {
    private static final String HA_SUBFLOW_ID_SHARED = "shared";
    private boolean active;
    private final FlowRepository commonFlowRepository;
    private final YFlowRepository yFlowRepository;
    private final HaFlowRepository haFlowRepository;

    private final KildaEntryCacheCarrier carrier;

    /**
     * Cookie to flow and meter to flow maps.
     */
    private final HashSetValuedHashMap<CookieCacheKey, KildaEntryDescriptor> cookieToFlow =
            new HashSetValuedHashMap<>();
    private final HashSetValuedHashMap<MeterCacheKey, KildaEntryDescriptor> switchAndMeterToFlow =
            new HashSetValuedHashMap<>();
    private final HashSetValuedHashMap<GroupCacheKey, KildaEntryDescriptor> switchAndGroupToFlow =
            new HashSetValuedHashMap<>();

    public KildaEntryCacheService(PersistenceManager persistenceManager, KildaEntryCacheCarrier carrier) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.commonFlowRepository = repositoryFactory.createFlowRepository();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
        this.haFlowRepository = repositoryFactory.createHaFlowRepository();
        this.carrier = carrier;
        this.active = false;
    }

    /**
     * Activates the service. Fills cache in particular.
     */
    public void activate() {
        if (!active) {
            refreshCache();
        }
        active = true;
    }

    /**
     * Deactivates the service. Clears cache in particular.
     */
    public boolean deactivate() {
        if (active) {
            clearCache();
        }
        active = false;
        return true;
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
            if (cookieToFlow.get(key).isEmpty()) {
                stats.add(entry, null);
            } else {
                cookieToFlow.get(key).forEach(descriptor -> stats.add(entry, descriptor));
            }
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
            if (switchAndMeterToFlow.get(key).isEmpty()) {
                stats.add(entry, null);
            } else {
                switchAndMeterToFlow.get(key).forEach(descriptor -> stats.add(entry, descriptor));
            }
        }
        carrier.emitMeterStats(stats);
    }

    /**
     * Process the provided {@link GroupStatsData} by completing it with cached data and forwarding it.
     *
     * @param data the data to process.
     */
    public void completeAndForwardGroupStats(GroupStatsData data) {
        SwitchGroupStats stats = new SwitchGroupStats(data.getSwitchId());
        for (GroupStatsEntry entry : data.getStats()) {
            GroupCacheKey key = new GroupCacheKey(data.getSwitchId(), entry.getGroupId());
            if (switchAndGroupToFlow.get(key).isEmpty()) {
                stats.add(entry, null);
            } else {
                switchAndGroupToFlow.get(key).forEach(descriptor -> stats.add(entry, descriptor));
            }
        }
        carrier.emitGroupStats(stats);
    }

    /**
     * Removed the provided path info from the cache.
     *
     * @param removePathInfo the path info to apply.
     */
    public void removeCachedHa(RemoveHaFlowPathInfo removePathInfo) {
        CacheRemoveHandler cacheRemoveHandler
                = new CacheRemoveHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow);
        updateCacheHa(cacheRemoveHandler, removePathInfo.getHaFlowId(), removePathInfo.getPathNodes(),
                removePathInfo.getCookie(), removePathInfo.getMeterId(),
                removePathInfo.getYPointGroupId(),
                removePathInfo.getYPointMeterId(), removePathInfo.getHaSubFlowId(),
                removePathInfo.getYPointSwitchId());
    }

    /**
     * Update the cache with the provided path info.
     *
     * @param updatePathInfo the path info to apply.
     */
    public void addOrUpdateCache(UpdateFlowPathInfo updatePathInfo) {
        updateCache(
                new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow), updatePathInfo);
    }

    /**
     * Update the cache with the provided path info.
     *
     * @param yFlowStatsInfo the path info to apply.
     */
    public void addOrUpdateCache(UpdateYFlowStatsInfo yFlowStatsInfo) {
        updateCache(
                new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow), yFlowStatsInfo
        );
    }

    /**
     * Removed the provided path info from the cache.
     *
     * @param removePathInfo the path info to apply.
     */
    public void removeCached(RemoveFlowPathInfo removePathInfo) {
        updateCache(new CacheRemoveHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow), removePathInfo);
    }

    /**
     * Removed the provided path info from the cache.
     *
     * @param yFlowStatsInfo the path info to apply.
     */
    public void removeCached(RemoveYFlowStatsInfo yFlowStatsInfo) {
        updateCache(new CacheRemoveHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow), yFlowStatsInfo);
    }

    /**
     * Update the cache with the provided path info.
     *
     * @param updatePathInfo the path info to apply.
     */
    public void addOrUpdateCacheHa(UpdateHaFlowPathInfo updatePathInfo) {
        CacheAddUpdateHandler cacheAddUpdateHandler =
                new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow);
        updateCacheHa(cacheAddUpdateHandler, updatePathInfo.getHaFlowId(), updatePathInfo.getPathNodes(),
                updatePathInfo.getCookie(), updatePathInfo.getMeterId(),
                updatePathInfo.getYPointGroupId(),
                updatePathInfo.getYPointMeterId(), updatePathInfo.getHaSubFlowId(),
                updatePathInfo.getYPointSwitchId());
    }

    private void updateCacheHa(KildaEntryDescriptorHandler cacheHandler,
                               String haFlowId, List<PathNodePayload> pathNodes,
                               FlowSegmentCookie cookie, MeterId meterId,
                               GroupId ypointGroupId, MeterId yPointMeterId,
                               String haSubFlowId, SwitchId yPointSwitchId) {
        if (pathNodes.isEmpty()) {
            throw new IllegalArgumentException("The path can't be empty");
        }
        processHaCookies(cacheHandler, haFlowId, cookie, meterId, pathNodes, ypointGroupId, yPointMeterId,
                haSubFlowId, yPointSwitchId);
    }

    private void updateCache(KildaEntryDescriptorHandler cacheHandler, BaseFlowPathInfo pathInfo) {
        updateCache(
                cacheHandler, pathInfo.getFlowId(), pathInfo.getYFlowId(), pathInfo.getYPointSwitchId(),
                pathInfo.getCookie(), pathInfo.getMeterId(), pathInfo.getPathNodes(), pathInfo.getStatVlans(),
                pathInfo.isIngressMirror(), pathInfo.isEgressMirror());
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

    private void updateCache(
            KildaEntryDescriptorHandler cacheHandler, String flowId, String yFlowId, SwitchId yPointSwitchId,
            FlowSegmentCookie cookie, MeterId meterId, List<PathNodePayload> pathNodes, Set<Integer> statsVlan,
            boolean ingressMirror, boolean egressMirror) {
        if (pathNodes.isEmpty()) {
            throw new IllegalArgumentException("The path can't be empty");
        }

        processTransitCookies(cacheHandler, flowId, yFlowId, yPointSwitchId, cookie, pathNodes);

        SwitchId srcSwitchId = pathNodes.get(0).getSwitchId();
        SwitchId dstSwitchId = pathNodes.get(pathNodes.size() - 1).getSwitchId();

        boolean isOneSwitchFlow = srcSwitchId.equals(dstSwitchId);
        if (isOneSwitchFlow) {
            cacheHandler.handle(newEndpointPathEntry(srcSwitchId, ONE_SWITCH, flowId, yFlowId, yPointSwitchId,
                    cookie, meterId, ingressMirror || egressMirror));
        } else {
            cacheHandler.handle(newEndpointPathEntry(
                    srcSwitchId, INGRESS, flowId, yFlowId, yPointSwitchId, cookie, meterId, ingressMirror));
            cacheHandler.handle(newEndpointPathEntry(
                    dstSwitchId, EGRESS, flowId, yFlowId, yPointSwitchId, cookie, null, egressMirror));
        }
        cacheHandler.handle(new StatVlanDescriptor(srcSwitchId, INGRESS, flowId, cookie, statsVlan));
        cacheHandler.handle(new StatVlanDescriptor(dstSwitchId, EGRESS, flowId, cookie, statsVlan));
    }

    private void refreshCommonFlowsCache() {
        CacheAddUpdateHandler cacheHandler =
                new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow);
        commonFlowRepository.findAll().stream()
                .flatMap(flow -> flow.getPaths().stream())
                .filter(Objects::nonNull)
                .forEach(path -> {
                    Flow flow = path.getFlow();
                    boolean ingressMirror = path.getFlowMirrorPointsSet().stream()
                            .anyMatch(point -> point.getMirrorSwitchId().equals(path.getSrcSwitchId()));
                    boolean egressMirror = path.getFlowMirrorPointsSet().stream()
                            .anyMatch(point -> point.getMirrorSwitchId().equals(path.getDestSwitchId()));
                    SwitchId yPointSwitchId = Optional.ofNullable(flow.getYFlow())
                            .map(YFlow::getYPoint)
                            .orElse(null);
                    updateCache(
                            cacheHandler, flow.getFlowId(), flow.getYFlowId(), yPointSwitchId, path.getCookie(),
                            path.getMeterId(), FlowPathMapper.INSTANCE.mapToPathNodes(flow, path),
                            flow.getVlanStatistics(), ingressMirror, egressMirror);
                });
    }

    private void refreshHaFlowsCache() {
        CacheAddUpdateHandler cacheHandler =
                new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow);
        haFlowRepository.findAll().forEach(haFlow -> processHaFlow(haFlow, cacheHandler));
    }

    private void processHaFlow(HaFlow haFlow, CacheAddUpdateHandler cacheHandler) {
        haFlow.getPaths().forEach(haPath -> processHaPath(haPath, cacheHandler));
    }

    private void processHaPath(HaFlowPath haPath, CacheAddUpdateHandler cacheHandler) {
        haPath.getSubPaths().forEach(subPath -> updateCacheHa(
                cacheHandler, subPath.getHaFlowId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(subPath.getHaFlowPath().getHaFlow(), subPath),
                subPath.getCookie(),
                subPath.getMeterId(),
                subPath.getCookie().getDirection() == FlowPathDirection.FORWARD
                        ? subPath.getHaFlowPath().getYPointGroupId() : null,
                subPath.getCookie().getDirection() == FlowPathDirection.REVERSE
                        ? subPath.getHaFlowPath().getYPointMeterId() : null,
                subPath.getHaSubFlowId(),
                subPath.getHaFlowPath().getYPointSwitchId()));
    }

    private void refreshYFlowsCache() {
        CacheAddUpdateHandler cacheHandler =
                new CacheAddUpdateHandler(cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow);
        for (YFlow entry : yFlowRepository.findAll()) {
            if (entry.getSharedEndpointMeterId() == null) {
                continue;
            }
            if (entry.getMeterId() == null) {
                continue;
            }

            updateCache(
                    cacheHandler, entry.getYFlowId(), entry.getSharedEndpoint().getSwitchId(),
                    entry.getSharedEndpointMeterId(), Y_FLOW_SHARED);
            updateCache(
                    cacheHandler, entry.getYFlowId(), entry.getYPoint(), entry.getMeterId(),
                    MeasurePoint.Y_FLOW_Y_POINT);
        }
    }

    private void processTransitCookies(
            KildaEntryDescriptorHandler cacheHandler, String flowId, String yFlowId, SwitchId yPointSwitchId,
            FlowSegmentCookie cookie, List<PathNodePayload> path) {
        // Skip the first and the last nodes as they're handled as INGRESS and EGRESS.
        for (int i = 1; i < path.size() - 1; i++) {
            SwitchId transitSrc = path.get(i).getSwitchId();
            cacheHandler.handle(newTransitPathEntry(transitSrc, flowId, yFlowId, yPointSwitchId, cookie));
        }
    }

    private static KildaEntryDescriptor newTransitPathEntry(
            SwitchId switchId, String flowId, String yFlowId, SwitchId yPointSwitchId, FlowSegmentCookie cookie) {
        if (yFlowId != null) {
            return new YFlowSubDescriptor(switchId, TRANSIT, yFlowId, flowId, yPointSwitchId, cookie, null);
        }
        return new CommonFlowDescriptor(switchId, TRANSIT, flowId, cookie, null);
    }

    private static KildaEntryDescriptor newEndpointPathEntry(
            SwitchId switchId, MeasurePoint measurePoint, String flowId, String yFlowId, SwitchId yPointSwitchId,
            FlowSegmentCookie cookie, MeterId meterId, boolean hasMirror) {
        if (yFlowId != null) {
            return new YFlowSubDescriptor(switchId, measurePoint, yFlowId, flowId, yPointSwitchId, cookie, meterId);
        }
        return new EndpointFlowDescriptor(switchId, measurePoint, flowId, cookie, meterId, hasMirror);
    }

    /**
     * Processes HA cookies for a given flow, generating and handling flow descriptors based on the provided parameters.
     *
     * @param cacheHandler   the KildaEntryDescriptorHandler used to handle the flow descriptors
     * @param haFlowId       the ID of the HA flow
     * @param cookie         the FlowSegmentCookie associated with the flow
     * @param meterId        the MeterId associated with the flow
     * @param path           the list of PathNodePayload representing the flow path
     * @param ypointGroupId  the GroupId associated with the y-point switch
     * @param yPointMeterId  the MeterId associated with the y-point switch
     * @param haSubFlowId    the ID of the HA subflow
     * @param yPointSwitchId the SwitchId of the y-point switch
     */
    private void processHaCookies(
            KildaEntryDescriptorHandler cacheHandler, String haFlowId,
            FlowSegmentCookie cookie, MeterId meterId, List<PathNodePayload> path, GroupId ypointGroupId,
            MeterId yPointMeterId, String haSubFlowId, SwitchId yPointSwitchId) {

        List<SwitchId> switchIds = path.stream().map(PathNodePayload::getSwitchId).collect(Collectors.toList());
        int yPointSwitchPosition = findYPointSwitchPositionHaFlow(switchIds, yPointSwitchId, haFlowId);

        for (int i = 0; i < path.size(); i++) {
            boolean isShared = isSharedSwitch(i, yPointSwitchPosition, cookie);
            SwitchId sw = path.get(i).getSwitchId();

            FlowSegmentCookie modifiedCookie = isShared
                    ? cookie.toBuilder().subType(FlowSegmentCookie.FlowSubType.SHARED).build() : cookie;
            //ingress
            if (i == 0) {
                cacheHandler.handle(newHaFlowDescriptor(sw, INGRESS, haFlowId, modifiedCookie,
                        meterId, null, null, resolveHaSubFlowId(isShared, haSubFlowId)));
            }
            //y-point
            if (sw.equals(yPointSwitchId)) {
                cacheHandler.handle(newHaFlowDescriptor(sw, HA_FLOW_Y_POINT, haFlowId, modifiedCookie, null,
                        ypointGroupId, yPointMeterId, resolveHaSubFlowIdForYPoint(cookie, haSubFlowId)));
            } else if (i > 0 && i < path.size() - 1) {
                // do not send meter into a transit
                cacheHandler.handle(newCommonHaFlowDescriptor(sw, TRANSIT, haFlowId, modifiedCookie,
                        null, null, resolveHaSubFlowId(isShared, haSubFlowId)));
            }
            //egress
            if (i == path.size() - 1) {
                cacheHandler.handle(newHaFlowDescriptor(sw, EGRESS, haFlowId, modifiedCookie,
                        null, null, null, resolveHaSubFlowId(isShared, haSubFlowId)));
            }
        }
    }

    private String resolveHaSubFlowId(boolean isShared, String haSubFlowId) {
        return isShared ? HA_SUBFLOW_ID_SHARED : haSubFlowId;
    }

    private String resolveHaSubFlowIdForYPoint(FlowSegmentCookie cookie, String haSubFlowId) {
        return cookie.getDirection() == FlowPathDirection.FORWARD ? HA_SUBFLOW_ID_SHARED : haSubFlowId;
    }

    /**
     * Checks if a switch is a shared switch based on its position in the path and the yPointSwitchPosition.
     * If switch is a shared switch then it means that it belongs to both subFlows
     *
     * @param switchPositionInPath the position of the switch in the path
     * @param yPointSwitchPosition the position of the yPointSwitch in the path
     * @param cookie               the FlowSegmentCookie associated with the switch
     * @return true if the switch is a shared switch, false otherwise
     */
    private boolean isSharedSwitch(int switchPositionInPath, int yPointSwitchPosition, FlowSegmentCookie cookie) {
        if (switchPositionInPath < 0 || cookie.getDirection() == FlowPathDirection.UNDEFINED) {
            return false;
        }
        if (cookie.getDirection() == FlowPathDirection.FORWARD) {
            return switchPositionInPath <= yPointSwitchPosition;
        } else {
            return switchPositionInPath > yPointSwitchPosition;
        }
    }


    /**
     * Finds the position of a given y-point SwitchId in the list of switchIds.
     *
     * @param switchIds      the list of SwitchIds to search in
     * @param yPointSwitchId the SwitchId to find the position of
     * @return the index of the yPointSwitchId in the switchIds list, or -1 if it is not found
     */
    private int findYPointSwitchPositionHaFlow(List<SwitchId> switchIds, SwitchId yPointSwitchId, String haFlowId) {
        for (int i = 0; i < switchIds.size(); i++) {
            if (switchIds.get(i).equals(yPointSwitchId)) {
                return i;
            }
        }
        log.error("There is no y-point switch for ha-flow with id: {}", haFlowId);
        throw new IllegalArgumentException(String.format("Y-point switch not found for haFlowId: %s", haFlowId));
    }

    private static KildaEntryDescriptor newHaFlowDescriptor(
            SwitchId switchId, MeasurePoint measurePoint, String haFlowId, FlowSegmentCookie cookie,
            MeterId meterId, GroupId ypointGroupId, MeterId yPointMeterId, String haSubFlowId) {
        return new HaFlowDescriptor(switchId, measurePoint, haFlowId, cookie, meterId, false,
                ypointGroupId, yPointMeterId, haSubFlowId);
    }

    private static KildaEntryDescriptor newCommonHaFlowDescriptor(
            SwitchId switchId, MeasurePoint measurePoint, String haFlowId, FlowSegmentCookie cookie,
            GroupId ypointGroupId, MeterId yPointMeterId, String haSubFlowId) {
        return new CommonHaFlowDescriptor(switchId, measurePoint, haFlowId, cookie, null, ypointGroupId,
                yPointMeterId, haSubFlowId);
    }

    /**
     * Refresh the cache by rereading the flow data from the persistence.
     */
    private void refreshCache() {
        refreshCommonFlowsCache();
        refreshHaFlowsCache();
        refreshYFlowsCache();
        log.debug("cookieToFlow cache: {}, switchAndMeterToFlow cache: {}, switchAndGroupToFlow cache: {}",
                cookieToFlow, switchAndMeterToFlow, switchAndGroupToFlow);
    }

    private void clearCache() {
        cookieToFlow.clear();
        switchAndMeterToFlow.clear();
        switchAndGroupToFlow.clear();
    }
}
