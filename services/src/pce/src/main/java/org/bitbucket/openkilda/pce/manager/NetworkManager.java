package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.NetworkStorage;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private MutableNetwork<Switch, Isl> network;

    /**
     * Switches pool.
     */
    private Map<String, Switch> switchPool;

    /**
     * Isl pool.
     */
    private Map<String, Isl> islPool;

    /**
     * {@link NetworkStorage} instance.
     */
    private NetworkStorage networkStorage;


    /**
     * Instance constructor.
     *
     * @param networkStorage {@link NetworkStorage} instance
     */
    public NetworkManager(NetworkStorage networkStorage) {
        this.networkStorage = networkStorage;

        if (network == null) {
            network = NetworkBuilder.directed()
                    .allowsSelfLoops(false)
                    .allowsParallelEdges(true)
                    .build();

            logger.info("Load Switch Pool");
            Set<Switch> switchList = networkStorage.dumpSwitches();

            logger.debug("Switch List: {}", switchList);
            switchPool = switchList.stream()
                    .collect(Collectors.toMap(Switch::getSwitchId, sw -> sw));
            switchList.forEach(network::addNode);

            logger.info("Load Isl Pool");
            Set<Isl> islList = networkStorage.dumpIsls();

            logger.debug("Isl List: {}", islList);
            islPool = islList.stream()
                    .collect(Collectors.toMap(Isl::getId, isl -> isl));
            islList.forEach(isl -> network.addEdge(
                    switchPool.get(isl.getSourceSwitch()),
                    switchPool.get(isl.getDestinationSwitch()),
                    isl));
        }
    }

    /**
     * Clears the inner network and pools.
     */
    public void clear() {
        islPool.values().forEach(isl -> network.removeEdge(isl));
        islPool.clear();
        switchPool.values().forEach(node -> network.removeNode(node));
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
        String switchId = newSwitch.getSwitchId();

        logger.debug("Create {} switch with {} parameters", switchId, newSwitch);

        Switch oldSwitch = switchPool.get(switchId);
        if (oldSwitch != null) {
            throw new IllegalArgumentException(String.format("Switch %s already exists", switchId));
        }

        networkStorage.createSwitch(newSwitch);
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
        logger.debug("Update {} switch with {} parameters", switchId, newSwitch);

        Switch oldSwitch = deleteSwitch(switchId);
        createSwitch(newSwitch);

        return oldSwitch;
    }

    /**
     * Deletes {@link Switch} instance.
     *
     * @param switchId {@link Switch} instance id
     * @return removed {@link Switch} instance
     * @throws IllegalArgumentException if {@link Switch} instance with specified id does not exist
     */
    public Switch deleteSwitch(String switchId) throws IllegalArgumentException {
        logger.debug("Delete {} switch", switchId);

        Switch node = switchPool.remove(switchId);
        if (node == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", switchId));
        }

        networkStorage.deleteSwitch(switchId);
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
    private EndpointPair<Switch> getIslSwitches(Isl isl) throws IllegalArgumentException {
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
        String islId = isl.getId();
        logger.debug("Create or update {} isl with {} parameters", islId, isl);

        EndpointPair<Switch> nodes;
        Isl oldIsl = islPool.get(islId);
        if (oldIsl == null) {
            networkStorage.createIsl(isl);
            nodes = getIslSwitches(isl);
        } else {
            networkStorage.updateIsl(islId, isl);
            nodes = network.incidentNodes(oldIsl);
            network.removeEdge(oldIsl);
        }
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
        logger.debug("Delete {} isl", islId);

        Isl isl = islPool.remove(islId);
        if (isl == null) {
            throw new IllegalArgumentException(String.format("Isl %s not found", islId));
        }

        networkStorage.deleteIsl(islId);
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
}
