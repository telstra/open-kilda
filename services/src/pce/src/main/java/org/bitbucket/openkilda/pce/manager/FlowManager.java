package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.pce.Utils;
import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.FlowStorage;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FlowManager class contains basic operations on flows.
 */
public class FlowManager {
    static final long FORWARD_FLOW_COOKIE_MASK = 0x4000000000000000L;
    static final long REVERSE_FLOW_COOKIE_MASK = 0x4000000000000000L;
    static final long FLOW_COOKIE_VALUE_MASK = 0x00000000FFFFFFFFL;

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowManager.class);

    /**
     * {@link ResourceManager} instance.
     */
    @VisibleForTesting
    final ResourceManager resourceManager = new ResourceManager();

    /**
     * Flow pool.
     */
    private final Map<String, ImmutablePair<Flow, Flow>> flowPool = new ConcurrentHashMap<>();

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
     * Flow change event callback.
     */
    private Function<FlowChangeEvent, Void> onFlowChange;

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

        logger.info("Load Flow Pool");
        Set<ImmutablePair<Flow, Flow>> flows = flowStorage.dumpFlows();

        logger.debug("Flow Set: {}", flows);
        flows.forEach(flow -> createFlowCache(flow.left.getFlowId(), flow));
    }

    /**
     * Sets flow change event callback.
     *
     * @param onFlowChange flow change event callback
     * @return this instance
     */
    public FlowManager withFlowChange(Function<FlowChangeEvent, Void> onFlowChange) {
        this.onFlowChange = onFlowChange;
        return this;
    }

    /**
     * Clears the inner network and pools.
     */
    public void clear() {
        flowPool.clear();
        resourceManager.clear();
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
     * @param newFlow flow
     */
    public ImmutablePair<Flow, Flow> createFlow(Flow newFlow) {
        String flowId = newFlow.getFlowId();
        ImmutablePair<Flow, Flow> flow = createFlowCache(flowId, buildFlow(newFlow));

        flowStorage.createFlow(flow);

        flowChanged(new FlowChangeEvent(flow, null, null));

        return flow;
    }

    /**
     * Creates flow.
     *
     * @param newFlow flow
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
     */
    public ImmutablePair<Flow, Flow> deleteFlow(String flowId) {
        ImmutablePair<Flow, Flow> flow = deleteFlowCache(flowId);

        flowStorage.deleteFlow(flowId);

        flowChanged(new FlowChangeEvent(null, null, flow));

        return flow;
    }

    /**
     * Deletes flow.
     *
     * @param flowId flow id
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
     */
    public ImmutablePair<Flow, Flow> updateFlow(String flowId, Flow newFlow) {
        ImmutablePair<Flow, Flow> flow = updateFlowCache(flowId, buildFlow(newFlow));

        flowStorage.updateFlow(flowId, flow);

        flowChanged(new FlowChangeEvent(null, flow, null));

        return flow;
    }

    /**
     * Updates flow.
     *
     * @param flowId  flow id
     * @param newFlow flow
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
                        || flow.getRight().getFlowPath().stream().anyMatch(isl -> isl.getSourceSwitch().equals(switchId))
                        || isOneSwitchFlow(flow) && flow.getLeft().getSourceSwitch().equals(switchId))
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
    public ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        logger.debug("Get path between source switch {} and destination switch {}", srcSwitch, dstSwitch);
        LinkedList<Isl> forwardPath = pathComputer.getPath(srcSwitch, dstSwitch, bandwidth);
        LinkedList<Isl> reversePath = pathComputer.getPath(dstSwitch, srcSwitch, bandwidth);
        return new ImmutablePair<>(forwardPath, reversePath);
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitch source {@link Switch} instance
     * @param dstSwitch destination {@link Switch} instance
     * @param bandwidth available bandwidth
     * @return {@link LinkedList} of {@link Isl} instances
     */
    public LinkedList<Isl> getSinglePath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        logger.debug("Get single path between source switch {} and destination switch {}", srcSwitch, dstSwitch);
        return pathComputer.getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link LinkedList} of {@link Isl} instances
     * @param secondPath second {@link LinkedList} of {@link Isl} instances
     * @return intersection {@link Set} of {@link Isl} instances
     */
    public ImmutablePair<Set<Isl>, Set<Isl>> getPathIntersection(
            ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> firstPath,
            ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> secondPath) {
        logger.debug("Get path intersection between {} and {}", firstPath, secondPath);

        Set<Isl> forwardIntersection = getSinglePathIntersection(firstPath.left, secondPath.left);
        Set<Isl> reverseIntersection = getSinglePathIntersection(firstPath.right, secondPath.right);

        ImmutablePair<Set<Isl>, Set<Isl>> intersection = new ImmutablePair<>(forwardIntersection, reverseIntersection);

        logger.debug("Path intersection is {}", intersection);

        return intersection;
    }

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link LinkedList} of {@link Isl} instances
     * @param secondPath second {@link LinkedList} of {@link Isl} instances
     * @return intersection {@link Set} of {@link Isl} instances
     */
    public Set<Isl> getSinglePathIntersection(LinkedList<Isl> firstPath, LinkedList<Isl> secondPath) {
        logger.debug("Get single path intersection between {} and {}", firstPath, secondPath);

        Set<Isl> intersection = new HashSet<>(firstPath);
        intersection.retainAll(secondPath);

        logger.debug("Single path intersection is {}", intersection);

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
    ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> getPath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        Switch srcSwitch = networkManager.getSwitch(srcSwitchId);
        Switch dstSwitch = networkManager.getSwitch(dstSwitchId);
        return getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Gets path between source and destination switches.
     *
     * @param srcSwitchId source {@link Switch} id
     * @param dstSwitchId destination {@link Switch} id
     * @param bandwidth   available bandwidth
     * @return {@link LinkedList} of {@link Isl} instances
     */
    LinkedList<Isl> getSinglePath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        Switch srcSwitch = networkManager.getSwitch(srcSwitchId);
        Switch dstSwitch = networkManager.getSwitch(dstSwitchId);
        return getSinglePath(srcSwitch, dstSwitch, bandwidth);
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
        int cookie = resourceManager.allocateCookie();

        ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> path =
                getPath(flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth());

        Flow forward = buildForwardFlow(flow, cookie, path.left, timestamp);
        pathComputer.updatePathBandwidth(path.left, flow.getBandwidth());

        Flow reverse = buildReverseFlow(flow, cookie, path.right, timestamp);
        pathComputer.updatePathBandwidth(path.right, flow.getBandwidth());

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

    /**
     * Checks if flow is through single switch.
     *
     * @param flow flow
     * @return true if source and destination switches are same for specified flow, otherwise false
     */
    public boolean isOneSwitchFlow(ImmutablePair<Flow, Flow> flow) {
        return flow.getLeft().getSourceSwitch().equals(flow.getRight().getSourceSwitch());
    }

    /**
     * Handles flow change event.
     *
     * @param event {@link FlowChangeEvent} instance
     */
    public void handleFlowChange(FlowChangeEvent event) {
        if (event.created != null) {
            createFlowCache(event.created.left.getFlowId(), event.created);
        }

        if (event.updated != null) {
            updateFlowCache(event.updated.left.getFlowId(), event.updated);
        }

        if (event.deleted != null) {
            deleteFlowCache(event.deleted.left.getFlowId());
        }
    }

    /**
     * Generates event.
     *
     * @param event {@link FlowChangeEvent} instance
     */
    private void flowChanged(FlowChangeEvent event) {
        if (onFlowChange != null) {
            onFlowChange.apply(event);
        }
    }

    /**
     * Flow changed event representation class.
     */
    class FlowChangeEvent {
        /**
         * Created flow instance.
         */
        public ImmutablePair<Flow, Flow> created;

        /**
         * Updated flow instance.
         */
        public ImmutablePair<Flow, Flow> updated;

        /**
         * Deleted flow instance.
         */
        public ImmutablePair<Flow, Flow> deleted;

        /**
         * Instance constructor.
         *
         * @param created created flow instance
         * @param updated updated flow instance
         * @param deleted deleted flow instance
         */
        FlowChangeEvent(ImmutablePair<Flow, Flow> created,
                        ImmutablePair<Flow, Flow> updated,
                        ImmutablePair<Flow, Flow> deleted) {
            this.created = created;
            this.updated = updated;
            this.deleted = deleted;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("created", created)
                    .add("updated", updated)
                    .add("deleted", deleted)
                    .toString();
        }
    }
}
