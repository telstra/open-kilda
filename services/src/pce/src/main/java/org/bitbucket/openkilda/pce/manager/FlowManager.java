package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.pce.Utils;
import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.FlowStorage;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FlowManager class contains basic operations on flows.
 */
public class FlowManager {
    private static final long FORWARD_FLOW_COOKIE_MASK = 0x4000000000000000L;
    private static final long REVERSE_FLOW_COOKIE_MASK = 0x4000000000000000L;
    private static final long FLOW_COOKIE_VALUE_MASK = 0x00000000FFFFFFFFL;

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowManager.class);

    /**
     * {@link ResourceManager} instance.
     */
    private final ResourceManager resourceManager = new ResourceManager();

    /**
     * {@link FlowStorage} instance.
     */
    private final FlowStorage flowStorage;

    /**
     * {@link NetworkManager} instance.
     */
    private final NetworkManager networkManager;

    /**
     * {@link PathComputer} instance.
     */
    private final PathComputer pathComputer;

    /**
     * Flow pool.
     */
    private Map<String, ImmutablePair<Flow, Flow>> flowPool;

    /**
     * Instance constructor.
     *
     * @param flowStorage    {@link FlowStorage} instance
     * @param networkManager {@link NetworkManager} instance
     * @param pathComputer   {@link PathComputer} instance
     */
    public FlowManager(FlowStorage flowStorage, NetworkManager networkManager, PathComputer pathComputer) {
        this.flowStorage = flowStorage;
        this.networkManager = networkManager;
        this.pathComputer = pathComputer;
        pathComputer.setNetwork(networkManager.getNetwork());

        if (flowPool == null) {
            Set<ImmutablePair<Flow, Flow>> flows = flowStorage.dumpFlows();
            flows.forEach(this::allocateFlow);
            flowPool = flows.stream().collect(Collectors.toMap(flow -> flow.left.getFlowId(), flow -> flow));
        }
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
            throw new IllegalArgumentException(String.format("Flow %s not found", flowId));
        }

        return flow;
    }

    /**
     * Creates flow.
     *
     * @param flow flow
     */
    public ImmutablePair<Flow, Flow> createFlow(Flow flow) {
        String flowId = flow.getFlowId();

        logger.debug("Create {} flow with {} parameters", flowId, flow);

        ImmutablePair<Flow, Flow> oldFlow = flowPool.get(flowId);
        if (oldFlow != null) {
            throw new IllegalArgumentException(String.format("Flow %s already exists", flowId));
        }

        ImmutablePair<Flow, Flow> newFlow = buildFlow(flow);

        flowStorage.createFlow(newFlow);

        flowPool.put(flowId, newFlow);

        return newFlow;
    }

    /**
     * Deletes flow.
     *
     * @param flowId flow id
     */
    public ImmutablePair<Flow, Flow> deleteFlow(String flowId) {
        logger.debug("Delete {} flow", flowId);

        ImmutablePair<Flow, Flow> flow = flowPool.remove(flowId);
        if (flow == null) {
            throw new IllegalArgumentException(String.format("Flow %s not found", flowId));
        }

        flowStorage.deleteFlow(flowId);
        deallocateFlow(flow);

        return flow;
    }

    /**
     * Updates flow.
     *
     * @param flowId  flow id
     * @param newFlow flow
     */
    public ImmutablePair<Flow, Flow> updateFlow(String flowId, Flow newFlow) {
        logger.debug("Update {} flow with {} parameters", flowId, newFlow);

        ImmutablePair<Flow, Flow> oldFlow = deleteFlow(flowId);
        createFlow(newFlow);

        return oldFlow;
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
     * Gets flow path.
     *
     * @param flowId flow id
     * @return flow path
     */
    public ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> getFlowPath(String flowId) {
        return new ImmutablePair<>(getFlow(flowId).left.getFlowPath(), getFlow(flowId).right.getFlowPath());
    }

    /**
     * Gets flows with specified switch in the path.
     *
     * @param switchId switch id
     * @return set of flows
     */
    public Set<ImmutablePair<Flow, Flow>> getAffectedBySwitchFlows(String switchId) {
        return flowPool.values().stream().filter(flow ->
                flow.getLeft().getFlowPath().stream().anyMatch(isl -> isl.getSourceSwitch().equals(switchId))
                        || flow.getRight().getFlowPath().stream().anyMatch(isl -> isl.getSourceSwitch().equals(switchId)))
                .collect(Collectors.toSet());
    }

    /**
     * Gets flows with specified isl in the path.
     *
     * @param islId isl id
     * @return set of flows
     */
    public Set<ImmutablePair<Flow, Flow>> getAffectedByIslFlows(String islId) {
        return flowPool.values().stream().filter(flow ->
                flow.getLeft().getFlowPath().stream().anyMatch(isl -> isl.getId().equals(islId))
                        || flow.getRight().getFlowPath().stream().anyMatch(isl -> isl.getId().equals(islId)))
                .collect(Collectors.toSet());
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitch source {@link Switch} instance
     * @param dstSwitch destination {@link Switch} instance
     * @param bandwidth available bandwidth
     * @return {@link LinkedList} of {@link Isl} instances
     */
    public LinkedList<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        logger.debug("Get path between source switch {} and destination switch {}", srcSwitch, dstSwitch);

        return pathComputer.getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link LinkedList} of {@link Isl} instances
     * @param secondPath second {@link LinkedList} of {@link Isl} instances
     * @return intersection {@link Set} of {@link Isl} instances
     */
    public Set<Isl> getPathIntersection(LinkedList<Isl> firstPath, LinkedList<Isl> secondPath) {
        logger.debug("Get path intersection between {} and {}", firstPath, secondPath);

        Set<Isl> intersection = new HashSet<>(firstPath);
        intersection.retainAll(secondPath);

        logger.debug("Path intersection is {}", intersection);

        return intersection;
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitchId source {@link Switch} id
     * @param dstSwitchId destination {@link Switch} id
     * @param bandwidth   available bandwidth
     * @return {@link LinkedList} of {@link Isl} instances
     */
    LinkedList<Isl> getPath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        Switch srcSwitch = networkManager.getSwitch(srcSwitchId);
        Switch dstSwitch = networkManager.getSwitch(dstSwitchId);
        return getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Allocates flow resources.
     *
     * @param flow flow
     */
    void allocateFlow(ImmutablePair<Flow, Flow> flow) {
        resourceManager.allocateCookie((int) (flow.left.getCookie() & FLOW_COOKIE_VALUE_MASK));
        resourceManager.allocateVlanId(flow.left.getTransitVlan());
        resourceManager.allocateMeterId(flow.left.getSourceSwitch(), flow.left.getMeterId());

        if (flow.right != null) {
            resourceManager.allocateCookie((int) (flow.right.getCookie() & FLOW_COOKIE_VALUE_MASK));
            resourceManager.allocateVlanId(flow.right.getTransitVlan());
            resourceManager.allocateMeterId(flow.right.getSourceSwitch(), flow.right.getMeterId());
        }
    }

    /**
     * Deallocates flow resources.
     *
     * @param flow flow
     */
    void deallocateFlow(ImmutablePair<Flow, Flow> flow) {
        resourceManager.deallocateCookie((int) (flow.left.getCookie() & FLOW_COOKIE_VALUE_MASK));
        resourceManager.deallocateVlanId(flow.left.getTransitVlan());
        resourceManager.deallocateMeterId(flow.left.getSourceSwitch(), flow.left.getMeterId());

        if (flow.right != null) {
            resourceManager.deallocateCookie((int) (flow.right.getCookie() & FLOW_COOKIE_VALUE_MASK));
            resourceManager.deallocateVlanId(flow.right.getTransitVlan());
            resourceManager.deallocateMeterId(flow.right.getSourceSwitch(), flow.right.getMeterId());
        }
    }

    /**
     * Builds new forward and reverse flow pair.
     *
     * @param flow source flow
     * @return new forward and reverse flow pair
     */
    ImmutablePair<Flow, Flow> buildFlow(Flow flow) {
        String timestamp = Utils.getIsoTimestamp();
        LinkedList<Isl> path = getPath(flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth());
        int cookie = resourceManager.allocateCookie();

        Flow forward = buildForwardFlow(flow, cookie, path, timestamp);
        pathComputer.updatePathBandwidth(path, flow.getBandwidth());

        Collections.reverse(path);

        Flow reverse = buildReverseFlow(flow, cookie, path, timestamp);
        pathComputer.updatePathBandwidth(path, flow.getBandwidth());

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
    Flow buildForwardFlow(Flow flow, int cookie, LinkedList<Isl> path, String timestamp) {
        return new Flow(flow.getFlowId(), flow.getBandwidth(), cookie | FORWARD_FLOW_COOKIE_MASK,
                flow.getDescription(), timestamp, flow.getSourceSwitch(), flow.getDestinationSwitch(),
                flow.getSourcePort(), flow.getDestinationPort(), flow.getSourceVlan(), flow.getDestinationVlan(),
                resourceManager.allocateMeterId(flow.getSourceSwitch()), resourceManager.allocateVlanId(), path);

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
    Flow buildReverseFlow(Flow flow, int cookie, LinkedList<Isl> path, String timestamp) {
        return new Flow(flow.getFlowId(), flow.getBandwidth(), cookie | REVERSE_FLOW_COOKIE_MASK,
                flow.getDescription(), timestamp, flow.getDestinationSwitch(), flow.getSourceSwitch(),
                flow.getDestinationPort(), flow.getSourcePort(), flow.getDestinationVlan(), flow.getSourceVlan(),
                resourceManager.allocateMeterId(flow.getDestinationSwitch()), resourceManager.allocateVlanId(), path);
    }
}
