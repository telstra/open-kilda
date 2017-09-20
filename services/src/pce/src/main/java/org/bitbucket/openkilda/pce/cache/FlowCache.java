package org.bitbucket.openkilda.pce.cache;

import org.bitbucket.openkilda.messaging.error.CacheException;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;
import org.bitbucket.openkilda.pce.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FlowCache extends Cache {
    /**
     * Forward flow cookie mask.
     */
    public static final long FORWARD_FLOW_COOKIE_MASK = 0x4000000000000000L;

    /**
     * Reverse flow cookie mask.
     */
    public static final long REVERSE_FLOW_COOKIE_MASK = 0x2000000000000000L;

    /**
     * Flow cookie value mask.
     */
    public static final long FLOW_COOKIE_VALUE_MASK = 0x00000000FFFFFFFFL;

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
        flows.forEach(this::createFlow);
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
     * @param flowId fow id
     * @param flow   flow
     * @return previous flow
     */
    public ImmutablePair<Flow, Flow> putFlow(String flowId, ImmutablePair<Flow, Flow> flow) {
        return flowPool.put(flowId, flow);
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
     * Gets flows with specified switch in the path.
     *
     * @param switchId switch id
     * @return set of flows
     */
    public Set<ImmutablePair<Flow, Flow>> getAffectedFlows(String switchId) {
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
    public Set<ImmutablePair<Flow, Flow>> getAffectedFlows(IslInfoData islData) {
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
    public Set<ImmutablePair<Flow, Flow>> getAffectedFlows(PortInfoData portData) {
        PathNode node = new PathNode(portData.getSwitchId(), portData.getPortNo(), 0);
        return flowPool.values().stream().filter(flow ->
                flow.getLeft().getFlowPath().getPath().contains(node)
                        || flow.getRight().getFlowPath().getPath().contains(node))
                .collect(Collectors.toSet());
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
        ImmutablePair<Flow, Flow> newFlow = buildFlow(flow, path);

        ImmutablePair<Flow, Flow> oldFlow = flowPool.get(flowId);
        if (oldFlow != null) {
            throw new CacheException(ErrorType.ALREADY_EXISTS, "Can not create flow",
                    String.format("Flow %s already exists", flowId));
        }

        allocateFlow(newFlow);
        flowPool.put(flowId, newFlow);

        return newFlow;
    }

    /**
     * Creates flow.
     *
     * @param flow flow
     * @return flow
     */
    public ImmutablePair<Flow, Flow> createFlow(ImmutablePair<Flow, Flow> flow) {
        String flowId = flow.left.getFlowId();
        logger.debug("Create {} flow with {} parameters", flowId, flow);

        ImmutablePair<Flow, Flow> oldFlow = flowPool.get(flowId);
        if (oldFlow != null) {
            throw new CacheException(ErrorType.ALREADY_EXISTS, "Can not create flow",
                    String.format("Flow %s already exists", flowId));
        }

        allocateFlow(flow);
        flowPool.put(flowId, flow);

        return flow;
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

        deallocateFlow(flow);

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
        ImmutablePair<Flow, Flow> newFlow = buildFlow(flow, path);

        ImmutablePair<Flow, Flow> odlFlow = flowPool.remove(flowId);
        if (odlFlow == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not update flow",
                    String.format("Flow %s not found", flowId));
        }
        deallocateFlow(odlFlow);

        allocateFlow(newFlow);
        flowPool.put(flowId, newFlow);

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
     *
     * @param flow source flow
     * @return new forward and reverse flow pair
     */
    ImmutablePair<Flow, Flow> buildFlow(Flow flow, ImmutablePair<PathInfoData, PathInfoData> path) {
        String timestamp = Utils.getIsoTimestamp();
        int cookie = resourceCache.allocateCookie();

        Flow forward = buildForwardFlow(flow, cookie, path.left, timestamp);
        Flow reverse = buildReverseFlow(flow, cookie, path.right, timestamp);

        return new ImmutablePair<>(forward, reverse);
    }

    /**
     * Builds new forward flow.
     *
     * @param flow      source flow
     * @param cookie    allocated cookie
     * @param path      found path
     * @param timestamp timestamp
     * @return forward flow
     */
    Flow buildForwardFlow(Flow flow, int cookie, PathInfoData path, String timestamp) {
        return new Flow(flow.getFlowId(), flow.getBandwidth(), cookie | FORWARD_FLOW_COOKIE_MASK,
                flow.getDescription(), timestamp, flow.getSourceSwitch(), flow.getDestinationSwitch(),
                flow.getSourcePort(), flow.getDestinationPort(), flow.getSourceVlan(), flow.getDestinationVlan(),
                resourceCache.allocateMeterId(flow.getSourceSwitch()), resourceCache.allocateVlanId(),
                path, FlowState.ALLOCATED);

    }

    /**
     * Builds new reverse flow.
     *
     * @param flow      source flow
     * @param cookie    allocated cookie
     * @param path      found path
     * @param timestamp timestamp
     * @return reverse flow
     */
    Flow buildReverseFlow(Flow flow, int cookie, PathInfoData path, String timestamp) {
        return new Flow(flow.getFlowId(), flow.getBandwidth(), cookie | REVERSE_FLOW_COOKIE_MASK,
                flow.getDescription(), timestamp, flow.getDestinationSwitch(), flow.getSourceSwitch(),
                flow.getDestinationPort(), flow.getSourcePort(), flow.getDestinationVlan(), flow.getSourceVlan(),
                resourceCache.allocateMeterId(flow.getDestinationSwitch()), resourceCache.allocateVlanId(),
                path, FlowState.ALLOCATED);
    }

    /**
     * Allocates flow resources.
     *
     * @param flow flow
     */
    @VisibleForTesting
    void allocateFlow(ImmutablePair<Flow, Flow> flow) {
        resourceCache.allocateCookie((int) (flow.left.getCookie() & FLOW_COOKIE_VALUE_MASK));
        resourceCache.allocateVlanId(flow.left.getTransitVlan());
        resourceCache.allocateMeterId(flow.left.getSourceSwitch(), flow.left.getMeterId());

        if (flow.right != null) {
            resourceCache.allocateCookie((int) (flow.right.getCookie() & FLOW_COOKIE_VALUE_MASK));
            resourceCache.allocateVlanId(flow.right.getTransitVlan());
            resourceCache.allocateMeterId(flow.right.getSourceSwitch(), flow.right.getMeterId());
        }
    }

    /**
     * Deallocates flow resources.
     *
     * @param flow flow
     */
    @VisibleForTesting
    void deallocateFlow(ImmutablePair<Flow, Flow> flow) {
        resourceCache.deallocateCookie((int) (flow.left.getCookie() & FLOW_COOKIE_VALUE_MASK));
        resourceCache.deallocateVlanId(flow.left.getTransitVlan());
        resourceCache.deallocateMeterId(flow.left.getSourceSwitch(), flow.left.getMeterId());

        if (flow.right != null) {
            resourceCache.deallocateCookie((int) (flow.right.getCookie() & FLOW_COOKIE_VALUE_MASK));
            resourceCache.deallocateVlanId(flow.right.getTransitVlan());
            resourceCache.deallocateMeterId(flow.right.getSourceSwitch(), flow.right.getMeterId());
        }
    }

    /**
     * Checks if flow is through single switch.
     *
     * @param flow flow
     * @return true if source and destination switches are same for specified flow, otherwise false
     */
    private boolean isOneSwitchFlow(ImmutablePair<Flow, Flow> flow) {
        return flow.getLeft().getSourceSwitch().equals(flow.getRight().getSourceSwitch());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("resources", resourceCache)
                .add("flows", flowPool)
                .toString();
    }
}
