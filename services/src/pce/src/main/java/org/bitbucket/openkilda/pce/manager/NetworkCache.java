package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NetworkCache {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NetworkCache.class);

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
     * Gets all {@link Isl} instances which start node is specified {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Set} of {@link Isl} instances
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exists
     */
    public Set<Isl> getIslsBySource(String switchId) {
        logger.debug("Get all isls by source switch {}", switchId);

        Switch startNode = getSwitchCache(switchId);

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

        Switch endNode = getSwitchCache(switchId);

        return network.inEdges(endNode);
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

        Switch node = getSwitchCache(switchId);

        return network.incidentEdges(node);
    }

    /**
     * Gets all {@link Switch} instances in specified {@link SwitchState} state.
     *
     * @param state {@link SwitchState} state
     * @return {@link Set} of {@link Switch} instances
     */
    public Set<Switch> getStateSwitches(SwitchState state) {
        logger.debug("Get all switches in {} state", state);

        return network.nodes().stream()
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

        return network.nodes().stream()
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

        Switch node = getSwitchCache(switchId);

        return network.adjacentNodes(node);
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
     * Instance constructor.
     *
     * @param switches {@link Set} of {@link Switch} instances
     * @param isls     {@link Set} of {@link Isl} instances
     */
    NetworkCache(Set<Switch> switches, Set<Isl> isls) {
        logger.debug("Switches: {}", switches);
        switches.forEach(this::createSwitchCache);

        logger.debug("Isls: {}", isls);
        isls.forEach(isl -> createIslCache(isl.getId(), isl));
    }

    /**
     * Gets {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Switch} instance with specified {@link Switch} instance id
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    Switch getSwitchCache(String switchId) throws IllegalArgumentException {
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
    Switch createSwitchCache(Switch newSwitch) throws IllegalArgumentException {
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
    Switch updateSwitchCache(String switchId, Switch newSwitch) throws IllegalArgumentException {
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
    Switch deleteSwitchCache(String switchId) throws IllegalArgumentException {
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
    Set<Switch> dumpSwitchesCache() {
        logger.debug("Get all switches");

        return new HashSet<>(network.nodes());
    }

    /**
     * Checks if switch pool contains {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return true if switch pool contains {@link Switch} instance
     */
    boolean cacheContainsSwitch(String switchId) {
        logger.debug("Is switch {} in cache", switchId);

        return switchPool.containsKey(switchId);
    }

    /**
     * Get {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @return {@link Isl} instance with specified {@link Isl} instance id
     * @throws IllegalArgumentException if {@link Isl} instance with specified id does not exist
     */
    Isl getIslCache(String islId) throws IllegalArgumentException {
        logger.debug("Get {} isl", islId);

        Isl isl = islPool.get(islId);
        if (isl == null) {
            throw new IllegalArgumentException(String.format("Isl %s not found", islId));
        }

        return islPool.get(islId);
    }

    /**
     * Creates {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @param isl   {@link Isl} instance
     * @return {@link Isl} instance previously associated with this {@link Isl} instance id or null otherwise
     * @throws IllegalArgumentException if {@link Switch} instances for {@link Isl} instance do not exist
     */
    Isl createIslCache(String islId, Isl isl) throws IllegalArgumentException {
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
    Isl updateIslCache(String islId, Isl isl) throws IllegalArgumentException {
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
    Isl deleteIslCache(String islId) throws IllegalArgumentException {
        logger.debug("Delete {} isl", islId);

        Isl isl = islPool.remove(islId);
        if (isl == null) {
            throw new IllegalArgumentException(String.format("Isl %s not found", islId));
        }

        network.removeEdge(isl);

        return isl;
    }

    /**
     * Gets all {@link Isl} instances.
     *
     * @return {@link Set} of {@link Isl} instances
     */
    Set<Isl> dumpIslsCache() {
        logger.debug("Get all isls");

        return new HashSet<>(network.edges());
    }

    /**
     * Checks if isl pool contains {@link Isl} instance.
     *
     * @param islId {@link Isl} instance id
     * @return true if isl pool contains {@link Isl} instance
     */
    boolean cacheContainsIsl(String islId) {
        logger.debug("Is isl {} in cache", islId);

        return islPool.containsKey(islId);
    }

    /**
     * Gets internal network.
     *
     * @return internal network
     */
    protected MutableNetwork<Switch, Isl> getNetwork() {
        return network;
    }

    /**
     * Gets {@link Switch} instances which are incident nodes for specified {@link Isl} instance.
     *
     * @param isl {@link Isl} instance
     * @return {@link EndpointPair} of {@link Switch} instances
     * @throws IllegalArgumentException if {@link Switch} instances for {@link Isl} instance do not exist
     */
    private EndpointPair<Switch> getIslSwitches(Isl isl) throws IllegalArgumentException {
        String srcSwitch = isl.getSourceSwitch();
        if (srcSwitch == null) {
            throw new IllegalArgumentException("Source switch not specified");
        }

        Switch startNode = getSwitchCache(srcSwitch);

        String dstSwitch = isl.getDestinationSwitch();
        if (dstSwitch == null) {
            throw new IllegalArgumentException("Destination switch not specified");
        }

        Switch endNode = getSwitchCache(dstSwitch);

        return EndpointPair.ordered(startNode, endNode);
    }
}
