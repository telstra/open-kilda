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

package org.openkilda.pce.cache;

import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.pce.Utils;

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
    private final Map<String, ImmutablePair<Flow, Flow>> flowPool = new ConcurrentHashMap<>();

    /**
     * Fills cache.
     *
     * @param flows flows
     */
    public void load(Set<ImmutablePair<Flow, Flow>> flows) {
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
    public ImmutablePair<Flow, Flow> putFlow(ImmutablePair<Flow, Flow> flow) {
        return flowPool.put(flow.getLeft().getFlowId(), flow);
    }

    /**
     * Removes flow directly from the cache.
     *
     * @param flowId flow id
     * @return removed flow
     */
    public ImmutablePair<Flow, Flow> removeFlow(String flowId) {
        return flowPool.remove(flowId);
    }

    /**
     * Track and allocate the flow.
     *
     * @param flow The flow to track / allocate.
     */
    public void pushFlow(ImmutablePair<Flow, Flow> flow) {
        resourceCache.allocateFlow(flow);
        putFlow(flow);
    }

    /**
     * Checks if flow pool contains {@link Flow} instance.
     *
     * @param flowId {@link Flow} instance id
     * @return true if flow pool contains {@link Flow} instance
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
    public Set<ImmutablePair<Flow, Flow>> getActiveFlowsWithAffectedPath(SwitchId switchId) {
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
    public Set<ImmutablePair<Flow, Flow>> getActiveFlowsWithAffectedPath(IslInfoData islData) {
        return flowPool.values().stream()
                .filter(flow -> flow.getLeft().getFlowPath().getPath().contains(islData.getPath().get(0))
                        || flow.getRight().getFlowPath().getPath().contains(islData.getPath().get(0)))
                .filter(flow -> flow.getLeft().getState().isActiveOrCached())
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified switch and port in the path.
     *
     * @param portData port
     * @return set of flows
     */
    public Set<ImmutablePair<Flow, Flow>> getActiveFlowsWithAffectedPath(PortInfoData portData) {
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
    public Set<ImmutablePair<Flow, Flow>> getFlowsWithAffectedPath(SwitchId switchId) {
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
    public Set<ImmutablePair<Flow, Flow>> getFlowsWithAffectedPath(IslInfoData islData) {
        return flowPool.values().stream()
                .filter(flow -> flow.getLeft().getFlowPath().getPath().contains(islData.getPath().get(0))
                        || flow.getRight().getFlowPath().getPath().contains(islData.getPath().get(0)))
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified switch and port in the path.
     *
     * @param portData port
     * @return set of flows
     */
    public Set<ImmutablePair<Flow, Flow>> getFlowsWithAffectedPath(PortInfoData portData) {
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

        for (ImmutablePair<Flow, Flow> flow : flowPool.values()) {
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
    public ImmutablePair<PathInfoData, PathInfoData> getFlowPath(String flowId) {
        return new ImmutablePair<>(getFlow(flowId).left.getFlowPath(), getFlow(flowId).right.getFlowPath());
    }

    /**
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    public ImmutablePair<Flow, Flow> getFlow(String flowId) {
        logger.debug("Get {} flow", flowId);

        ImmutablePair<Flow, Flow> flow = flowPool.get(flowId);
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
    public ImmutablePair<Flow, Flow> createFlow(Flow flow, ImmutablePair<PathInfoData, PathInfoData> path) {
        String flowId = flow.getFlowId();
        logger.debug("Create {} flow with {} parameters", flowId, flow);

        ImmutablePair<Flow, Flow> oldFlow = flowPool.get(flowId);
        if (oldFlow != null) {
            throw new CacheException(ErrorType.ALREADY_EXISTS, "Can not create flow",
                    String.format("Flow %s already exists", flowId));
        }

        ImmutablePair<Flow, Flow> newFlow = buildFlow(flow, path);
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
    public ImmutablePair<Flow, Flow> deleteFlow(String flowId) {
        logger.debug("Delete {} flow", flowId);

        ImmutablePair<Flow, Flow> flow = flowPool.remove(flowId);
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
    public ImmutablePair<Flow, Flow> updateFlow(Flow flow, ImmutablePair<PathInfoData, PathInfoData> path) {
        String flowId = flow.getFlowId();
        logger.debug("Update {} flow with {} parameters", flowId, flow);

        ImmutablePair<Flow, Flow> oldFlow = flowPool.remove(flowId);
        if (oldFlow == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not update flow",
                    String.format("Flow %s not found", flowId));
        }

        ImmutablePair<Flow, Flow> newFlow;
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
    public Set<ImmutablePair<Flow, Flow>> dumpFlows() {
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
    public ImmutablePair<Set<PathNode>, Set<PathNode>> getPathIntersection(
            ImmutablePair<PathInfoData, PathInfoData> firstPath,
            ImmutablePair<PathInfoData, PathInfoData> secondPath) {
        logger.debug("Get path intersection between {} and {}", firstPath, secondPath);

        Set<PathNode> forwardIntersection = getPathIntersection(firstPath.left, secondPath.left);
        Set<PathNode> reverseIntersection = getPathIntersection(firstPath.right, secondPath.right);

        ImmutablePair<Set<PathNode>, Set<PathNode>> intersection =
                new ImmutablePair<>(forwardIntersection, reverseIntersection);

        logger.debug("Path intersection is {}", intersection);

        return intersection;
    }

    /**
     * Builds new forward and reverse flow pair.
     */
    private ImmutablePair<Flow, Flow> buildFlow(final Flow flow,
                                               ImmutablePair<PathInfoData, PathInfoData> path) {
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

        Flow.FlowBuilder forwardBuilder = Flow.builder()
                .flowId(flow.getFlowId())
                .cookie(cookie | ResourceCache.FORWARD_FLOW_COOKIE_MASK)
                .description(flow.getDescription())
                .lastUpdated(timestamp)
                .sourceSwitch(flow.getSourceSwitch())
                .destinationSwitch(flow.getDestinationSwitch())
                .sourcePort(flow.getSourcePort())
                .destinationPort(flow.getDestinationPort())
                .sourceVlan(flow.getSourceVlan())
                .destinationVlan(flow.getDestinationVlan())
                .transitVlan(forwardVlan)
                .flowPath(path.getLeft())
                .state(FlowState.ALLOCATED);
        setBandwidthAndMeter(forwardBuilder, flow.getBandwidth(), flow.isIgnoreBandwidth(),
                () -> resourceCache.allocateMeterId(flow.getSourceSwitch()));
        Flow forward = forwardBuilder.build();

        Flow.FlowBuilder reverseBuilder = Flow.builder()
                .flowId(flow.getFlowId())
                .cookie(cookie | ResourceCache.REVERSE_FLOW_COOKIE_MASK)
                .description(flow.getDescription())
                .lastUpdated(timestamp)
                .sourceSwitch(flow.getDestinationSwitch())
                .destinationSwitch(flow.getSourceSwitch())
                .sourcePort(flow.getDestinationPort())
                .destinationPort(flow.getSourcePort())
                .sourceVlan(flow.getDestinationVlan())
                .destinationVlan(flow.getSourceVlan())
                .transitVlan(reverseVlan)
                .flowPath(path.getRight())
                .state(FlowState.ALLOCATED);
        setBandwidthAndMeter(reverseBuilder, flow.getBandwidth(), flow.isIgnoreBandwidth(),
                () -> resourceCache.allocateMeterId(flow.getDestinationSwitch()));
        Flow reverse = reverseBuilder.build();

        return new ImmutablePair<>(forward, reverse);
    }

    private void setBandwidthAndMeter(Flow.FlowBuilder builder, long bandwidth, boolean isIgnoreBandwidth,
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
     * org.openkilda.messaging.model.Flow#isOneSwitchFlow()
     * </p>
     *
     * @param flow flow
     * @return true if source and destination switches are same for specified flow, otherwise false
     */
    public boolean isOneSwitchFlow(ImmutablePair<Flow, Flow> flow) {
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
    private SwitchId getFlowLinkedEndpoint(ImmutablePair<Flow, Flow> flow, SwitchId switchId) {
        Flow forward = flow.getLeft();
        Flow reverse = flow.getRight();
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
    public Set<Flow> getFlowsForEndpoint(SwitchId switchId, int port) {
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
    public Set<Flow> getFlowsForEndpoint(SwitchId switchId, int port, int vlan) {
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
    public Set<ImmutablePair<Flow, Flow>> getIngressAndEgressFlows(SwitchId switchId) {
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
