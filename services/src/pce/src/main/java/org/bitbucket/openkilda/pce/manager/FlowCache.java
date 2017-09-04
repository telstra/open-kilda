package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FlowCache extends BaseCache {
    /**
     * Forward flow cookie mask.
     */
    @VisibleForTesting
    static final long FORWARD_FLOW_COOKIE_MASK = 0x4000000000000000L;

    /**
     * Reverse flow cookie mask.
     */
    @VisibleForTesting
    static final long REVERSE_FLOW_COOKIE_MASK = 0x4000000000000000L;

    /**
     * Flow cookie value mask.
     */
    private static final long FLOW_COOKIE_VALUE_MASK = 0x00000000FFFFFFFFL;

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
        flows.forEach(flow -> createFlowCache(flow.left.getFlowId(), flow));
    }

    /**
     * Clears the inner network and pools.
     */
    public void clear() {
        flowPool.clear();
        resourceCache.clear();
    }

    /**
     * Gets flows with specified switch in the path.
     *
     * @param switchId switch id
     * @return set of flows
     */
    public Set<ImmutablePair<Flow, Flow>> getAffectedBySwitchFlows(String switchId) {
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
    public Set<ImmutablePair<Flow, Flow>> getAffectedByIslFlows(IslInfoData islData) {
        return flowPool.values().stream().filter(flow ->
                flow.getLeft().getFlowPath().getPath().contains(islData.getPath().get(0))
                        || flow.getLeft().getFlowPath().getPath().contains(islData.getPath().get(1))
                        || flow.getRight().getFlowPath().getPath().contains(islData.getPath().get(0))
                        || flow.getRight().getFlowPath().getPath().contains(islData.getPath().get(1)))
                .collect(Collectors.toSet());
    }

    /**
     * Gets flow path.
     *
     * @param flowId flow id
     * @return flow path
     */
    public ImmutablePair<PathInfoData, PathInfoData> getFlowPathCache(String flowId) {
        return new ImmutablePair<>(getFlowCache(flowId).left.getFlowPath(), getFlowCache(flowId).right.getFlowPath());
    }

    /**
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    public ImmutablePair<Flow, Flow> getFlowCache(String flowId) {
        logger.debug("Get {} flow", flowId);

        ImmutablePair<Flow, Flow> flow = flowPool.get(flowId);
        if (flow == null) {
            throw new IllegalArgumentException(String.format("Flow %s not found", flowId));
        }

        return flow;
    }

    /**
     * Creates flow.
     *
     * @param flowId  flow id
     * @param newFlow flow
     * @return flow
     */
    public ImmutablePair<Flow, Flow> createFlowCache(String flowId, ImmutablePair<Flow, Flow> newFlow) {
        logger.debug("Create {} flow with {} parameters", flowId, newFlow);

        ImmutablePair<Flow, Flow> oldFlow = flowPool.get(flowId);
        if (oldFlow != null) {
            throw new IllegalArgumentException(String.format("Flow %s already exists", flowId));
        }

        allocateFlow(newFlow);
        flowPool.put(flowId, newFlow);

        return newFlow;
    }

    /**
     * Deletes flow.
     *
     * @param flowId flow id
     * @return flow
     */
    public ImmutablePair<Flow, Flow> deleteFlowCache(String flowId) {
        logger.debug("Delete {} flow", flowId);

        ImmutablePair<Flow, Flow> flow = flowPool.remove(flowId);
        if (flow == null) {
            throw new IllegalArgumentException(String.format("Flow %s not found", flowId));
        }

        deallocateFlow(flow);

        return flow;
    }

    /**
     * Updates flow.
     *
     * @param flowId  flow id
     * @param newFlow flow
     * @return flow
     */
    public ImmutablePair<Flow, Flow> updateFlowCache(String flowId, ImmutablePair<Flow, Flow> newFlow) {
        logger.debug("Update {} flow with {} parameters", flowId, newFlow);

        ImmutablePair<Flow, Flow> odlFlow = flowPool.remove(flowId);
        if (odlFlow == null) {
            throw new IllegalArgumentException(String.format("Flow %s not found", flowId));
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
    public Set<ImmutablePair<Flow, Flow>> dumpFlowsCache() {
        logger.debug("Get all flows");
        return new HashSet<>(flowPool.values());
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
}
