package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.NetworkStorage;

import com.google.common.base.MoreObjects;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * NetworkManager class contains basic operations on network topology.
 */
public class NetworkManager {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NetworkManager.class);

    /**
     * Network cache.
     */
    private final MutableNetwork<Switch, Isl> network = NetworkBuilder
            .directed()
            .allowsSelfLoops(false)
            .allowsParallelEdges(true)
            .build();

    /**
     * Switches pool.
     */
    private final Map<String, Switch> switchPool = new ConcurrentHashMap<>();

    /**
     * Isl pool.
     */
    private final Map<String, Isl> islPool = new ConcurrentHashMap<>();

    /**
     * {@link NetworkStorage} instance.
     */
    private final NetworkStorage networkStorage;

    /**
     * Switch change event callback.
     */
    private Function<SwitchChangeEvent, Void> onSwitchChange;

    /**
     * Isl change event callback.
     */
    private Function<IslChangeEvent, Void> onIslChange;

    /**
     * Instance constructor.
     *
     * @param networkStorage {@link NetworkStorage} instance
     */
    public NetworkManager(NetworkStorage networkStorage) {
        this.networkStorage = networkStorage;

        logger.info("Load Switch Pool");
        Set<Switch> switchList = networkStorage.dumpSwitches();

        logger.debug("Switch Set: {}", switchList);
        switchList.forEach(this::createSwitchCache);

        logger.info("Load Isl Pool");
        Set<Isl> islList = networkStorage.dumpIsls();

        logger.debug("Isl Set: {}", islList);
        islList.forEach(isl -> createIslCache(isl.getId(), isl));
    }

    /**
     * Sets switch change event callback.
     *
     * @param onSwitchChange switch change event callback
     * @return this instance
     */
    public NetworkManager withSwitchChange(Function<SwitchChangeEvent, Void> onSwitchChange) {
        this.onSwitchChange = onSwitchChange;
        return this;
    }

    /**
     * Sets isl change event callback.
     *
     * @param onIslChange isl change event callback
     * @return this instance
     */
    public NetworkManager withIslChange(Function<IslChangeEvent, Void> onIslChange) {
        this.onIslChange = onIslChange;
        return this;
    }

    /**
     * Clears the inner network and pools.
     */
    public void clear() {
        islPool.values().forEach(network::removeEdge);
        islPool.clear();

        switchPool.values().forEach(network::removeNode);
        switchPool.clear();
    }

    /**
     * Gets internal network.
     *
     * @return internal network
     */
    public MutableNetwork<Switch, Isl> getNetwork() {
        return network;
    }

    /**
     * Gets {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Switch} instance with specified {@link Switch} instance id
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch getSwitch(String switchId) throws IllegalArgumentException {
        logger.debug("Get {} switch", switchId);

        Switch node = switchPool.get(switchId);
        if (node == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", switchId));
        }

        return node;
    }

    /**
     * Creates {@link Switch} instance.
     *
     * @param newSwitch {@link Switch} instance
     * @return created {@link Switch} instance
     * @throws IllegalArgumentException if {@link Switch} instance with specified id already exists
     */
    public Switch createSwitch(Switch newSwitch) throws IllegalArgumentException {
        Switch node = createSwitchCache(newSwitch);

        networkStorage.createSwitch(newSwitch);

        switchChanged(new SwitchChangeEvent(newSwitch, null, null));

        return node;
    }

    /**
     * Creates {@link Switch} instance.
     *
     * @param newSwitch {@link Switch} instance
     * @return created {@link Switch} instance
     * @throws IllegalArgumentException if {@link Switch} instance with specified id already exists
     */
    public Switch createSwitchCache(Switch newSwitch) throws IllegalArgumentException {
        String switchId = newSwitch.getSwitchId();

        logger.debug("Create {} switch with {} parameters", switchId, newSwitch);

        Switch oldSwitch = switchPool.get(switchId);
        if (oldSwitch != null) {
            throw new IllegalArgumentException(String.format("Switch %s already exists", switchId));
        }

        network.addNode(newSwitch);
        switchPool.put(switchId, newSwitch);

        return newSwitch;
    }

    /**
     * Updates {@link Switch} instance.
     *
     * @param switchId  {@link Switch} instance id
     * @param newSwitch {@link Switch} instance
     * @return {@link Switch} instance before update
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch updateSwitch(String switchId, Switch newSwitch) throws IllegalArgumentException {
        Switch node = updateSwitchCache(switchId, newSwitch);

        networkStorage.updateSwitch(switchId, newSwitch);

        switchChanged(new SwitchChangeEvent(null, newSwitch, null));

        return node;
    }

    /**
     * Updates {@link Switch} instance.
     *
     * @param switchId  {@link Switch} instance id
     * @param newSwitch {@link Switch} instance
     * @return {@link Switch} instance before update
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch updateSwitchCache(String switchId, Switch newSwitch) throws IllegalArgumentException {
        logger.debug("Update {} switch with {} parameters", switchId, newSwitch);

        Switch oldSwitch = switchPool.remove(switchId);
        if (oldSwitch == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", switchId));
        }

        network.removeNode(oldSwitch);
        network.addNode(newSwitch);
        switchPool.put(switchId, newSwitch);

        return newSwitch;
    }

    /**
     * Deletes {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return removed {@link Switch} instance
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch deleteSwitch(String switchId) throws IllegalArgumentException {
        Switch node = deleteSwitchCache(switchId);

        networkStorage.deleteSwitch(switchId);

        switchChanged(new SwitchChangeEvent(null, null, node));

        return node;
    }

    /**
     * Deletes {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return removed {@link Switch} instance
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch deleteSwitchCache(String switchId) throws IllegalArgumentException {
        logger.debug("Delete {} switch", switchId);

        Switch node = switchPool.remove(switchId);
        if (node == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", switchId));
        }

        network.removeNode(node);

        return node;
    }

    /**
     * Gets all {@link Switch} instances.
     *
     * @return {@link Set} of {@link Switch} instances
     */
    public Set<Switch> dumpSwitches() {
        logger.debug("Get all switches");

        return new HashSet<>(network.nodes());
    }

    /**
     * Gets all {@link Switch} instances in specified {@link SwitchState} state.
     *
     * @param state {@link SwitchState} state
     * @return {@link Set} of {@link Switch} instances
     */
    public Set<Switch> getStateSwitches(SwitchState state) {
        logger.debug("Get all switches in {} state", state);

        return switchPool.values().stream()
                .filter(sw -> sw.getState() == state)
                .collect(Collectors.toSet());
    }

    /**
     * Gets all {@link Switch} instances with specified controller ip address.
     *
     * @param controller controller ip address
     * @return {@link Set} of {@link Switch} instances
     */
    public Set<Switch> getControllerSwitches(String controller) {
        logger.debug("Get all switches connected to {} controller", controller);

        return switchPool.values().stream()
                .filter(sw -> sw.getController().equals(controller))
                .collect(Collectors.toSet());
    }

    /**
     * Gets all {@link Switch} instances directly connected to specified.
     *
     * @param switchId switch id
     * @return {@link Set} of {@link Switch} instances
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Set<Switch> getDirectlyConnectedSwitches(String switchId) throws IllegalArgumentException {
        logger.debug("Get all switches directly connected to {} switch ", switchId);

        Switch node = getSwitch(switchId);

        return network.adjacentNodes(node);
    }

    /**
     * Gets {@link Switch} instances which are incident nodes for specified {@link Isl} instance.
     *
     * @param isl {@link Isl} instance
     * @return {@link EndpointPair} of {@link Switch} instances
     * @throws IllegalArgumentException if {@link Switch} instances for {@link Isl} instance do not exist
     */
    EndpointPair<Switch> getIslSwitches(Isl isl) throws IllegalArgumentException {
        String srcSwitch = isl.getSourceSwitch();
        if (srcSwitch == null) {
            throw new IllegalArgumentException("Source switch not specified");
        }

        Switch startNode = getSwitch(srcSwitch);

        String dstSwitch = isl.getDestinationSwitch();
        if (dstSwitch == null) {
            throw new IllegalArgumentException("Destination switch not specified");
        }

        Switch endNode = getSwitch(dstSwitch);

        return EndpointPair.ordered(startNode, endNode);
    }

    /**
     * Creates {@link Isl} instance.
     *
     * @param isl {@link Isl} instance
     * @return {@link Isl} instance previously associated with this {@link Isl} instance id or null otherwise
     * @throws IllegalArgumentException if {@link Switch} instances for {@link Isl} instance do not exist
     */
    public Isl createOrUpdateIsl(Isl isl) throws IllegalArgumentException {
        Isl newIsl;
        String islId = isl.getId();
        logger.debug("Create or update {} isl with {} parameters", islId, isl);

        if (islPool.containsKey(islId)) {
            newIsl = updateIslCache(islId, isl);

            networkStorage.updateIsl(islId, isl);

            islChanged(new IslChangeEvent(null, isl, null));
        } else {
            newIsl = createIslCache(islId, isl);

            networkStorage.createIsl(isl);

            islChanged(new IslChangeEvent(isl, null, null));
        }

        return newIsl;
    }

    /**
     * Creates {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @param isl   {@link Isl} instance
     * @return {@link Isl} instance previously associated with this {@link Isl} instance id or null otherwise
     * @throws IllegalArgumentException if {@link Switch} instances for {@link Isl} instance do not exist
     */
    public Isl createIslCache(String islId, Isl isl) throws IllegalArgumentException {
        logger.debug("Create {} isl with {} parameters", islId, isl);

        EndpointPair<Switch> nodes = getIslSwitches(isl);
        network.addEdge(nodes.source(), nodes.target(), isl);

        return islPool.put(islId, isl);
    }

    /**
     * Updates {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @param isl   new {@link Isl} instance
     * @return {@link Isl} instance previously associated with this {@link Isl} instance id or null otherwise
     * @throws IllegalArgumentException if {@link Switch} instances for {@link Isl} instance do not exist
     */
    public Isl updateIslCache(String islId, Isl isl) throws IllegalArgumentException {
        logger.debug("Update {} isl with {} parameters", islId, isl);

        EndpointPair<Switch> nodes = getIslSwitches(isl);
        network.removeEdge(islPool.get(islId));
        network.addEdge(nodes.source(), nodes.target(), isl);

        return islPool.put(islId, isl);
    }

    /**
     * Deletes {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @return removed {@link Isl} instance
     * @throws IllegalArgumentException if {@link Isl} instance with specified id does not exist
     */
    public Isl deleteIsl(String islId) throws IllegalArgumentException {
        Isl isl = deleteIslCache(islId);

        networkStorage.deleteIsl(islId);

        islChanged(new IslChangeEvent(null, null, isl));

        return isl;
    }

    /**
     * Deletes {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @return removed {@link Isl} instance
     * @throws IllegalArgumentException if {@link Isl} instance with specified id does not exist
     */
    public Isl deleteIslCache(String islId) throws IllegalArgumentException {
        logger.debug("Delete {} isl", islId);

        Isl isl = islPool.remove(islId);
        if (isl == null) {
            throw new IllegalArgumentException(String.format("Isl %s not found", islId));
        }

        network.removeEdge(isl);

        return isl;
    }

    /**
     * Get {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @return {@link Isl} instance with specified {@link Isl} instance id
     * @throws IllegalArgumentException if {@link Isl} instance with specified id does not exist
     */
    public Isl getIsl(String islId) throws IllegalArgumentException {
        logger.debug("Get {} isl", islId);

        Isl isl = islPool.get(islId);
        if (isl == null) {
            throw new IllegalArgumentException(String.format("Isl %s not found", islId));
        }

        return islPool.get(islId);
    }

    /**
     * Gets all {@link Isl} instances.
     *
     * @return {@link Set} of {@link Isl} instances
     */
    public Set<Isl> dumpIsls() {
        logger.debug("Get all isls");

        return new HashSet<>(network.edges());
    }

    /**
     * Gets all {@link Isl} instances which start or end node is specified {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Set} of {@link Isl} instances
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exists
     */
    public Set<Isl> getIslsBySwitch(String switchId) throws IllegalArgumentException {
        logger.debug("Get all isls incident switch {}", switchId);

        Switch node = getSwitch(switchId);

        return network.incidentEdges(node);
    }

    /**
     * Gets all {@link Isl} instances which start node is specified {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Set} of {@link Isl} instances
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exists
     */
    public Set<Isl> getIslsBySource(String switchId) {
        logger.debug("Get all isls by source switch {}", switchId);

        Switch startNode = getSwitch(switchId);

        return network.outEdges(startNode);
    }

    /**
     * Gets all {@link Isl} instances which end node is specified {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Set} of {@link Isl} instances
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exists
     */
    public Set<Isl> getIslsByDestination(String switchId) {
        logger.debug("Get all isls by destination switch {}", switchId);

        Switch endNode = getSwitch(switchId);

        return network.inEdges(endNode);
    }

    /**
     * Handles switch change event.
     *
     * @param event {@link SwitchChangeEvent} instance
     */
    public void handleSwitchChange(SwitchChangeEvent event) {
        if (event.created != null) {
            createSwitchCache(event.created);
        }

        if (event.updated != null) {
            updateSwitchCache(event.updated.getSwitchId(), event.updated);
        }

        if (event.deleted != null) {
            deleteSwitchCache(event.deleted.getSwitchId());
        }
    }

    /**
     * Handles isl change event.
     *
     * @param event {@link IslChangeEvent} instance
     */
    public void handleIslChange(IslChangeEvent event) {
        if (event.created != null) {
            createIslCache(event.created.getId(), event.created);
        }

        if (event.updated != null) {
            updateIslCache(event.updated.getId(), event.updated);
        }

        if (event.deleted != null) {
            deleteIslCache(event.deleted.getId());
        }
    }

    /**
     * Generates event.
     *
     * @param event {@link IslChangeEvent} instance
     */
    private void islChanged(IslChangeEvent event) {
        if (onIslChange != null) {
            onIslChange.apply(event);
        }
    }

    /**
     * Generates event.
     *
     * @param event {@link SwitchChangeEvent} instance
     */
    private void switchChanged(SwitchChangeEvent event) {
        if (onSwitchChange != null) {
            onSwitchChange.apply(event);
        }
    }

    /**
     * Switch changed event representation class.
     */
    class SwitchChangeEvent {
        /**
         * Created switch instance.
         */
        public Switch created;

        /**
         * Updated switch instance.
         */
        public Switch updated;

        /**
         * Deleted switch instance.
         */
        public Switch deleted;

        /**
         * Instance constructor.
         *
         * @param created created switch instance
         * @param updated updated switch instance
         * @param deleted deleted switch instance
         */
        SwitchChangeEvent(Switch created,
                          Switch updated,
                          Switch deleted) {
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

    /**
     * Isl changed event representation class.
     */
    class IslChangeEvent {
        /**
         * Created isl instance.
         */
        public Isl created;

        /**
         * Updated isl instance.
         */
        public Isl updated;

        /**
         * Deleted isl instance.
         */
        public Isl deleted;

        /**
         * Instance constructor.
         *
         * @param created created isl instance
         * @param updated updated isl instance
         * @param deleted deleted isl instance
         */
        IslChangeEvent(Isl created,
                       Isl updated,
                       Isl deleted) {
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
