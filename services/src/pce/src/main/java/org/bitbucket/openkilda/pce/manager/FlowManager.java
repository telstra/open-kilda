package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.pce.Utils;
import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.FlowStorage;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Set;
import java.util.function.Function;

/**
 * FlowManager class contains basic operations on flows.
 */
public class FlowManager extends FlowCache {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowManager.class);

    /**
     * {@link FlowStorage} instance.
     */
    private final FlowStorage flowStorage;

    /**
     * {@link NetworkManager} instance.
     */
    private final NetworkManager networkManager;

    /**
     * Flow change event callback.
     */
    private Function<FlowChangeEvent, Void> onFlowChange;

    /**
     * Instance constructor.
     *
     * @param flowStorage    {@link FlowStorage} instance
     * @param networkManager {@link NetworkManager} instance
     */
    public FlowManager(FlowStorage flowStorage, NetworkManager networkManager) {
        super(flowStorage.dumpFlows());
        this.flowStorage = flowStorage;
        this.networkManager = networkManager;
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
     * Gets flow.
     *
     * @param flowId flow id
     * @return flow
     */
    public ImmutablePair<Flow, Flow> getFlow(String flowId) {
        return getFlowCache(flowId);
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
     * Gets all flows.
     *
     * @return all flows
     */
    public Set<ImmutablePair<Flow, Flow>> dumpFlows() {
        return dumpFlowsCache();
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
        LinkedList<Isl> forwardPath = networkManager.getPath(srcSwitch, dstSwitch, bandwidth);
        LinkedList<Isl> reversePath = networkManager.getPath(dstSwitch, srcSwitch, bandwidth);
        return new ImmutablePair<>(forwardPath, reversePath);
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

        Set<Isl> forwardIntersection = networkManager.getPathIntersection(firstPath.left, secondPath.left);
        Set<Isl> reverseIntersection = networkManager.getPathIntersection(firstPath.right, secondPath.right);

        ImmutablePair<Set<Isl>, Set<Isl>> intersection = new ImmutablePair<>(forwardIntersection, reverseIntersection);

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
    public ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> getPath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        Switch srcSwitch = networkManager.getSwitch(srcSwitchId);
        Switch dstSwitch = networkManager.getSwitch(dstSwitchId);
        return getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Gets flow path.
     *
     * @param flowId flow id
     * @return flow path
     */
    public ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> getFlowPath(String flowId) {
        return getFlowPathCache(flowId);
    }

    /**
     * Builds new forward and reverse flow pair.
     *
     * @param flow source flow
     * @return new forward and reverse flow pair
     */
    ImmutablePair<Flow, Flow> buildFlow(Flow flow) {
        String timestamp = Utils.getIsoTimestamp();
        int cookie = resourceCache.allocateCookie();

        ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> path =
                getPath(flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth());

        Flow forward = buildForwardFlow(flow, cookie, path.left, timestamp);
        networkManager.updatePathBandwidth(path.left, flow.getBandwidth());

        Flow reverse = buildReverseFlow(flow, cookie, path.right, timestamp);
        networkManager.updatePathBandwidth(path.right, flow.getBandwidth());

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
                resourceCache.allocateMeterId(flow.getSourceSwitch()), resourceCache.allocateVlanId(), path);

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
                resourceCache.allocateMeterId(flow.getDestinationSwitch()), resourceCache.allocateVlanId(), path);
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
