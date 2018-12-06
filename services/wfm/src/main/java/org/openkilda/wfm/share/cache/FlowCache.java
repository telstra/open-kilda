/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.share.cache;

import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPairDto;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlowCache extends Cache {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowCache.class);

    /**
     * {@link ResourceCache} instance.
     */
    @VisibleForTesting
    final ResourceCache resourceCache = new ResourceCache();

    /**
     * Flow pool.
     */
    private final Map<String, FlowPairDto<FlowDto, FlowDto>> flowPool = new ConcurrentHashMap<>();

    /**
     * Fills cache.
     *
     * @param flows flows
     */
    public void load(Set<FlowPairDto<FlowDto, FlowDto>> flows) {
        logger.debug("Flows: {}", flows);
        flows.forEach(this::putFlow);
    }

    /**
     * Clears the inner network and pools.
     */
    public void clear() {
        flowPool.clear();
        resourceCache.clear();
    }

    /**
     * Puts flow directly to the cache.
     *
     * @param flow flow
     * @return previous flow
     */
    public FlowPairDto<FlowDto, FlowDto> putFlow(FlowPairDto<FlowDto, FlowDto> flow) {
        return flowPool.put(flow.getLeft().getFlowId(), flow);
    }

    /**
     * Removes flow directly from the cache.
     *
     * @param flowId flow id
     * @return removed flow
     */
    public FlowPairDto<FlowDto, FlowDto> removeFlow(String flowId) {
        return flowPool.remove(flowId);
    }

    /**
     * Track and allocate the flow.
     *
     * @param flow The flow to track / allocate.
     */
    public void pushFlow(FlowPairDto<FlowDto, FlowDto> flow) {
        resourceCache.allocateFlow(flow);
        putFlow(flow);
    }

    /**
     * Checks if flow pool contains {@link FlowDto} instance.
     *
     * @param flowId {@link FlowDto} instance id
     * @return true if flow pool contains {@link FlowDto} instance
     */
    public boolean cacheContainsFlow(String flowId) {
        logger.debug("Is flow {} in cache", flowId);

        return flowPool.containsKey(flowId);
    }

    /**
     * Gets active or cached flows with specified switch in the path.
     *
     * @param switchId switch id
     * @return set of flows
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> getActiveFlowsWithAffectedPath(SwitchId switchId) {
        return flowPool.values().stream().filter(flow ->
                flow.getLeft().getFlowPath().getPath().stream()
                        .anyMatch(node -> node.getSwitchId().equals(switchId))
                        || flow.getRight().getFlowPath().getPath().stream()
                        .anyMatch(node -> node.getSwitchId().equals(switchId))
                        || isOneSwitchFlow(flow) && flow.getLeft().getSourceSwitch().equals(switchId))
                .filter(flow -> flow.getLeft().getState().isActiveOrCached())
                .collect(Collectors.toSet());
    }

    /**
     * Gets active flows with specified isl in the path.
     *
     * @param islData isl
     * @return set of flows
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> getActiveFlowsWithAffectedPath(IslInfoData islData) {
        return flowPool.values().stream()
                .filter(flow -> flow.getLeft().getFlowPath().getPath().contains(islData.getSource())
                        || flow.getRight().getFlowPath().getPath().contains(islData.getSource()))
                .filter(flow -> flow.getLeft().getState().isActiveOrCached())
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified switch and port in the path.
     *
     * @param portData port
     * @return set of flows
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> getActiveFlowsWithAffectedPath(PortInfoData portData) {
        PathNode node = new PathNode(portData.getSwitchId(), portData.getPortNo(), 0);
        return flowPool.values().stream()
                .filter(flow -> flow.getLeft().getFlowPath().getPath().contains(node)
                        || flow.getRight().getFlowPath().getPath().contains(node))
                .filter(flow -> flow.getLeft().getState().isActiveOrCached())
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified switch in the path.
     *
     * @param switchId switch id
     * @return set of flows
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> getFlowsWithAffectedPath(SwitchId switchId) {
        return flowPool.values().stream().filter(flow ->
                flow.getLeft().getFlowPath().getPath().stream()
                        .anyMatch(node -> node.getSwitchId().equals(switchId))
                        || flow.getRight().getFlowPath().getPath().stream()
                        .anyMatch(node -> node.getSwitchId().equals(switchId))
                        || isOneSwitchFlow(flow) && flow.getLeft().getSourceSwitch().equals(switchId))
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified isl in the path.
     *
     * @param islData isl
     * @return set of flows
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> getFlowsWithAffectedPath(IslInfoData islData) {
        return flowPool.values().stream()
                .filter(flow -> flow.getLeft().getFlowPath().getPath().contains(islData.getSource())
                        || flow.getRight().getFlowPath().getPath().contains(islData.getSource()))
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified switch and port in the path.
     *
     * @param portData port
     * @return set of flows
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> getFlowsWithAffectedPath(PortInfoData portData) {
        PathNode node = new PathNode(portData.getSwitchId(), portData.getPortNo(), 0);
        return flowPool.values().stream().filter(flow ->
                flow.getLeft().getFlowPath().getPath().contains(node)
                        || flow.getRight().getFlowPath().getPath().contains(node))
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows for state change.
     *
     * @param switchId switch id
     * @return map of flow ids and endpoints
     */
    public Map<String, String> getFlowsWithAffectedEndpoint(SwitchId switchId) {
        Map<String, String> response = new HashMap<>();

        for (FlowPairDto<FlowDto, FlowDto> flow : flowPool.values()) {
            SwitchId endpoint = getFlowLinkedEndpoint(flow, switchId);
            if (endpoint != null) {
                response.put(flow.getLeft().getFlowId(), endpoint.toString());
            }
        }

        return response;
    }

    /**
     * Gets flow path.
     *
     * @param flowId flow id
     * @return flow path
     */
    public FlowPairDto<PathInfoData, PathInfoData> getFlowPath(String flowId) {
        return new FlowPairDto<>(getFlow(flowId).left.getFlowPath(), getFlow(flowId).right.getFlowPath());
    }

    /**
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    public FlowPairDto<FlowDto, FlowDto> getFlow(String flowId) {
        logger.debug("Get {} flow", flowId);

        FlowPairDto<FlowDto, FlowDto> flow = flowPool.get(flowId);
        if (flow == null) {
            // TODO: Is this really an exception? Should we just return null or empty?
            //      Feels like the caller should address this, and anticipate empty.
            throw new CacheException(ErrorType.NOT_FOUND, "Can not get flow",
                    String.format("Flow %s not found", flowId));
        }

        return flow;
    }

    /**
     * Creates flow.
     *
     * @param flow flow
     * @param path flow path
     * @return flow
     */
    public FlowPairDto<FlowDto, FlowDto> createFlow(FlowDto flow, FlowPairDto<PathInfoData, PathInfoData> path) {
        String flowId = flow.getFlowId();
        logger.debug("Create {} flow with {} parameters", flowId, flow);

        FlowPairDto<FlowDto, FlowDto> oldFlow = flowPool.get(flowId);
        if (oldFlow != null) {
            throw new CacheException(ErrorType.ALREADY_EXISTS, "Can not create flow",
                    String.format("Flow %s already exists", flowId));
        }

        FlowPairDto<FlowDto, FlowDto> newFlow = buildFlow(flow, path);
        resourceCache.allocateFlow(newFlow);
        flowPool.put(flowId, newFlow);

        return newFlow;
    }

    /**
     * Deletes flow.
     *
     * @param flowId flow id
     * @return flow
     */
    public FlowPairDto<FlowDto, FlowDto> deleteFlow(String flowId) {
        logger.debug("Delete {} flow", flowId);

        FlowPairDto<FlowDto, FlowDto> flow = flowPool.remove(flowId);
        if (flow == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not delete flow",
                    String.format("Flow %s not found", flowId));
        }

        resourceCache.deallocateFlow(flow);

        return flow;
    }

    /**
     * Updates flow.
     *
     * @param flow flow
     * @param path flow path
     * @return flow
     */
    public FlowPairDto<FlowDto, FlowDto> updateFlow(FlowDto flow, FlowPairDto<PathInfoData, PathInfoData> path) {
        String flowId = flow.getFlowId();
        logger.debug("Update {} flow with {} parameters", flowId, flow);

        FlowPairDto<FlowDto, FlowDto> oldFlow = flowPool.remove(flowId);
        if (oldFlow == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not update flow",
                    String.format("Flow %s not found", flowId));
        }

        FlowPairDto<FlowDto, FlowDto> newFlow;
        try {
            newFlow = buildFlow(flow, path);
            resourceCache.deallocateFlow(oldFlow);

            resourceCache.allocateFlow(newFlow);
            flowPool.put(flowId, newFlow);
        } catch (Throwable e) {
            flowPool.put(flowId, oldFlow);
            throw e;
        }

        return newFlow;
    }

    /**
     * Gets all flows.
     *
     * @return all flows
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> dumpFlows() {
        logger.debug("Get all flows");
        return new HashSet<>(flowPool.values());
    }

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link PathInfoData} instances
     * @param secondPath second {@link PathInfoData} instances
     * @return intersection {@link Set} of {@link IslInfoData} instances
     */
    public Set<PathNode> getPathIntersection(PathInfoData firstPath, PathInfoData secondPath) {
        logger.debug("Get single path intersection between {} and {}", firstPath, secondPath);
        Set<PathNode> intersection = new HashSet<>(firstPath.getPath());
        intersection.retainAll(secondPath.getPath());
        return intersection;
    }

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link LinkedList} of {@link PathInfoData} instances
     * @param secondPath second {@link LinkedList} of {@link PathInfoData} instances
     * @return intersection {@link Set} of {@link PathNode} instances
     */
    public FlowPairDto<Set<PathNode>, Set<PathNode>> getPathIntersection(
            FlowPairDto<PathInfoData, PathInfoData> firstPath,
            FlowPairDto<PathInfoData, PathInfoData> secondPath) {
        logger.debug("Get path intersection between {} and {}", firstPath, secondPath);

        Set<PathNode> forwardIntersection = getPathIntersection(firstPath.left, secondPath.left);
        Set<PathNode> reverseIntersection = getPathIntersection(firstPath.right, secondPath.right);

        FlowPairDto<Set<PathNode>, Set<PathNode>> intersection =
                new FlowPairDto<>(forwardIntersection, reverseIntersection);

        logger.debug("Path intersection is {}", intersection);

        return intersection;
    }

    /**
     * Builds new forward and reverse flow pair.
     */
    private FlowPairDto<FlowDto, FlowDto> buildFlow(final FlowDto flow,
                                                    FlowPairDto<PathInfoData, PathInfoData> path) {
        // FIXME(surabujin): format datetime as '2011-12-03T10:15:30Z' (don't match with format used in TE)
        String timestamp = Utils.getIsoTimestamp();
        int cookie = resourceCache.allocateCookie();

        /*
         * If either side is a SingleSwitchFlow .. don't allocate a vlan.
         */
        int forwardVlan;
        int reverseVlan;
        if (!flow.isOneSwitchFlow()) {
            forwardVlan = resourceCache.allocateVlanId();
            reverseVlan = resourceCache.allocateVlanId();
        } else {
            forwardVlan = reverseVlan = 0;
        }

        FlowDto.FlowDtoBuilder forwardBuilder = flow.toBuilder()
                .cookie(cookie | Flow.FORWARD_FLOW_COOKIE_MASK)
                .lastUpdated(timestamp)
                .transitVlan(forwardVlan)
                .flowPath(path.getLeft())
                .state(FlowState.ALLOCATED);
        setBandwidthAndMeter(forwardBuilder, flow.getBandwidth(), flow.isIgnoreBandwidth(),
                () -> resourceCache.allocateMeterId(flow.getSourceSwitch()));
        FlowDto forward = forwardBuilder.build();

        FlowDto.FlowDtoBuilder reverseBuilder = flow.toBuilder()
                .cookie(cookie | Flow.REVERSE_FLOW_COOKIE_MASK)
                .lastUpdated(timestamp)
                .transitVlan(reverseVlan)
                .flowPath(path.getRight())
                .state(FlowState.ALLOCATED)
                .sourceSwitch(flow.getDestinationSwitch())
                .sourcePort(flow.getDestinationPort())
                .sourceVlan(flow.getDestinationVlan())
                .destinationSwitch(flow.getSourceSwitch())
                .destinationPort(flow.getSourcePort())
                .destinationVlan(flow.getSourceVlan());
        setBandwidthAndMeter(reverseBuilder, flow.getBandwidth(), flow.isIgnoreBandwidth(),
                () -> resourceCache.allocateMeterId(flow.getDestinationSwitch()));
        FlowDto reverse = reverseBuilder.build();

        return new FlowPairDto<>(forward, reverse);
    }

    private void setBandwidthAndMeter(FlowDto.FlowDtoBuilder builder, long bandwidth, boolean isIgnoreBandwidth,
                                      Supplier<Integer> meterIdSupplier) {
        builder.bandwidth(bandwidth);

        if (bandwidth > 0L) {
            builder.ignoreBandwidth(isIgnoreBandwidth);
            builder.meterId(meterIdSupplier.get());
        } else {
            // When the flow is unmetered.
            builder.ignoreBandwidth(true);
            builder.meterId(0);
        }
    }

    /**
     * Checks if flow is through single switch.
     *
     * <p>
     * FIXME(surabujin): looks extremely over engineered. Can be replaces with
     * org.openkilda.messaging.model.FlowDto#isOneSwitchFlow()
     * </p>
     *
     * @param flow flow
     * @return true if source and destination switches are same for specified flow, otherwise false
     */
    public boolean isOneSwitchFlow(FlowPairDto<FlowDto, FlowDto> flow) {
        return flow.getLeft().getSourceSwitch().equals(flow.getLeft().getDestinationSwitch())
                && flow.getRight().getSourceSwitch().equals(flow.getRight().getDestinationSwitch());
    }

    /**
     * Gets flow linked with specified switch id.
     *
     * @param flow     flow
     * @param switchId switch id
     * @return second endpoint if specified switch id one of the flows endpoint, otherwise null
     */
    private SwitchId getFlowLinkedEndpoint(FlowPairDto<FlowDto, FlowDto> flow, SwitchId switchId) {
        FlowDto forward = flow.getLeft();
        FlowDto reverse = flow.getRight();
        SwitchId linkedSwitch = null;

        if (forward.getSourceSwitch().equals(switchId) && reverse.getDestinationSwitch().equals(switchId)) {
            linkedSwitch = forward.getDestinationSwitch();
        } else if (forward.getDestinationSwitch().equals(switchId) && reverse.getSourceSwitch().equals(switchId)) {
            linkedSwitch = forward.getSourceSwitch();
        }
        return linkedSwitch;
    }

    /**
     * Gets flows with specified switch and port.
     *
     * @param switchId the switch ID
     * @param port the port
     * @return set of flows
     */
    public Set<FlowDto> getFlowsForEndpoint(SwitchId switchId, int port) {
        return flowPool.values().stream()
                .flatMap(pair -> Stream.of(pair.getLeft(), pair.getRight()))
                .filter(flow -> flow.getSourceSwitch().equals(switchId) && flow.getSourcePort() == port
                        || flow.getDestinationSwitch().equals(switchId) && flow.getDestinationPort() == port)
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified switch, port and vlan.
     *
     * <p>
     * NOTE: The result set also includes flows that match switch, port and with no VLAN (vlan = 0) defined.
     * </p>
     *
     * @param switchId the switch ID
     * @param port the port
     * @param vlan the vlan
     * @return set of flows
     */
    public Set<FlowDto> getFlowsForEndpoint(SwitchId switchId, int port, int vlan) {
        return flowPool.values().stream()
                .flatMap(pair -> Stream.of(pair.getLeft(), pair.getRight()))
                .filter(flow -> flow.getSourceSwitch().equals(switchId) && flow.getSourcePort() == port
                        && (flow.getSourceVlan() == vlan || flow.getSourceVlan() == 0)
                        || flow.getDestinationSwitch().equals(switchId) && flow.getDestinationPort() == port
                        && (flow.getDestinationVlan() == vlan || flow.getDestinationVlan() == 0))
                .collect(Collectors.toSet());
    }


    /**
     * Gets flow pairs which have source or destination is on the switch.
     */
    public Set<FlowPairDto<FlowDto, FlowDto>> getIngressAndEgressFlows(SwitchId switchId) {
        return flowPool.values().stream()
                .filter(flowPair -> Objects.nonNull(getFlowLinkedEndpoint(flowPair, switchId)))
                .collect(Collectors.toSet());
    }


    public Set<Integer> getAllocatedVlans() {
        return resourceCache.getAllVlanIds();
    }

    public Set<Integer> getAllocatedCookies() {
        return resourceCache.getAllCookies();
    }

    public Map<SwitchId, Set<Integer>> getAllocatedMeters() {
        return resourceCache.getAllMeterIds();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("resources", resourceCache)
                .add("flows", flowPool)
                .toString();
    }
}
