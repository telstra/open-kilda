package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.path.PathComputer;
import org.bitbucket.openkilda.pce.storage.Storage;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Topology class contains basic operations on network topology.
 */
public class Topology {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(Topology.class);

    /**
     * Singleton {@link Topology} instance.
     */
    private static final Topology TOPOLOGY = new Topology();

    /**
     * Flows in process of reconfiguration.
     */
    private static final Map<String, Flow> flowsInReconfiguration = new ConcurrentHashMap<>();

    /**
     * Network cache.
     */
    private static MutableNetwork<Switch, Isl> network;

    /**
     * Switches pool.
     */
    private static Map<String, Switch> switchPool;

    /**
     * Flow pool.
     */
    private static Map<String, Flow> flowPool;

    /**
     * Isl pool.
     */
    private static Map<String, Isl> islPool;

    /**
     * {@link Storage} instance.
     */
    private static Storage storage;

    /**
     * {@link PathComputer} instance.
     */
    private static PathComputer pathComputer;

    /**
     * Singleton {@link Topology} instance constructor.
     */
    private Topology() {
    }

    /**
     * Returns topology instance with lazy initialization.
     *
     * @param storage {@link Storage} instance
     * @return singleton {@link Topology} instance
     */
    public static Topology getTopologyManager(Storage storage, PathComputer pathComputer) {
        Topology.storage = storage;
        Topology.pathComputer = pathComputer;

        if (network == null) {
            network = NetworkBuilder.directed().allowsSelfLoops(false).allowsParallelEdges(true).build();
            pathComputer.setNetwork(network);

            logger.info("Load Switch Pool");
            List<Switch> switchList = storage.dumpSwitches();

            logger.debug("Switch List: {}", switchList);
            switchPool = switchList.stream()
                    .collect(Collectors.toMap(Switch::getSwitchId, item -> item));
            switchList.forEach(network::addNode);

            logger.info("Load Isl Pool");
            List<Isl> islList = storage.dumpIsls();

            logger.debug("Isl List: {}", islList);
            islPool = islList.stream()
                    .collect(Collectors.toMap(Isl::getId, item -> item));
            islList.forEach(isl -> network.addEdge(
                    switchPool.get(isl.getSourceSwitch()),
                    switchPool.get(isl.getDestinationSwitch()),
                    isl));

            /*
            logger.info("Load Flow Pool");
            List<Flow> flowList = storage.dumpFlows();

            logger.debug("Flow List: {}", flowList);
            flowPool = flowList.stream()
                    .collect(Collectors.toMap(Flow::getFlowId, item -> item));
            flowList.forEach(Topology::loadResources);
            */
        }

        return TOPOLOGY;
    }

    /**
     * Clears the inner network and pools.
     */
    static void clear() {
        islPool.values().forEach(isl -> network.removeEdge(isl));
        islPool.clear();

        switchPool.values().forEach(node -> network.removeNode(node));
        switchPool.clear();

        //flowPool.clear();
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

        Switch node = switchPool.get(switchId);
        if (node != null) {
            throw new IllegalArgumentException(String.format("Switch %s already exists", switchId));
        }

        storage.createSwitch(newSwitch);
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

        storage.deleteSwitch(switchId);
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
            storage.createIsl(isl);
            nodes = getIslSwitches(isl);
        } else {
            storage.updateIsl(islId, isl);
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

        storage.deleteIsl(islId);
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
     * Gets all {@link Isl} instances which start or end node is specified {@link Switch} instance
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
     * Gets all {@link Isl} instances which start node is specified {@link Switch} instance
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
     * Gets all {@link Isl} instances which end node is specified {@link Switch} instance
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
     * Gets path between source and destination switches.
     *
     * @param srcSwitch source {@link Switch} instance
     * @param dstSwitch destination {@link Switch} instance
     * @return {@link Set} of {@link Isl} instances
     */
    public LinkedList<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        logger.debug("Get path between source switch {} and destination switch {}", srcSwitch, dstSwitch);

        return pathComputer.getPath(srcSwitch, dstSwitch, bandwidth);
    }

    /**
     * Gets {@link Switch} instance.
     *
     * @param flowId {@link Flow} instance id
     * @return {@link Flow} instance with specified {@link Flow} instance id
     * @throws IllegalArgumentException if {@link Flow} instance with specified id does not exist
     */
    /*
    public Flow getFlow(String flowId) {
        logger.debug("Get {} flow", flowId);

        Flow flow = flowPool.get(flowId);
        if (flow == null) {
            throw new IllegalArgumentException(String.format("Flow %s not found", flowId));
        }

        return flow;
    }
    */

    /**
     * Creates {@link Flow} instance.
     *
     * @param flow {@link Flow} instance
     * @param path {@link Set} of {@link Isl} instances, computed if null
     * @return created {@link Flow} instance
     */
    /*
    public Flow createFlow(Flow flow, Set<Isl> path) {
        String flowId = flow.getFlowId();
        logger.debug("Create {} flow with {} parameters", flowId, flow);

        Flow oldFlow = flowPool.get(flowId);
        if (oldFlow != null) {
            throw new IllegalArgumentException(String.format("Flow %s already exists", flowId));
        }

        if (path == null) {
            path = getPath(flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth());
        }
        allocateResources(flow, path);

        storage.createFlow(flow);
        flowPool.put(flowId, flow);

        return flow;
    }
    */

    /**
     * Creates {@link Flow} instance.
     *
     * @param flow {@link Flow} instance
     * @return created {@link Flow} instance
     */
    /*
    public Flow createFlow(Flow flow) {
        return createFlow(flow, null);
    }
    */

    /**
     * Deletes {@link Flow} instance.
     *
     * @param flowId {@link Flow} instance id
     * @return deleted {@link Flow} instance
     */
    /*
    public Flow deleteFlow(String flowId) {
        logger.debug("Delete {} flow", flowId);

        Flow flow = flowPool.remove(flowId);
        if (flow == null) {
            throw new IllegalArgumentException(String.format("Flow %s not found", flowId));
        }

        deallocateResources(flow);

        storage.deleteFlow(flowId);

        return flow;
    }
    */

    /**
     * Updates {@link Flow} instance.
     *
     * @param flowId {@link Flow} instance id
     * @param flow {@link Flow} instance
     * @param path {@link Set} of {@link Isl} instances, computed if null
     * @return updated {@link Flow} instance
     */
    /*
    public Flow updateFlow(String flowId, Flow flow, Set<Isl> path) {
        logger.debug("Update {} flow with {} parameters", flowId, flow);

        Flow oldFlow = getFlow(flowId);

        deallocateResources(oldFlow);
        if (path == null) {
            path = getPath(flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth());
        }
        allocateResources(flow, path);

        storage.updateFlow(flowId, flow);
        flowPool.put(flowId, flow);

        return oldFlow;
    }
    */

    /**
     * Updates {@link Flow} instance.
     *
     * @param flowId {@link Flow} instance id
     * @param flow {@link Flow} instance
     * @return updated {@link Flow} instance
     */
    /*
    public Flow updateFlow(String flowId, Flow flow) {
        return updateFlow(flowId, flow, null);
    }
    */

    /**
     * Gets all {@link Flow} instances.
     *
     * @return {@link Set} of {@link Flow} instances
     */
    /*
    public Set<Flow> dumpFlows() {
        logger.debug("Get all flows");

        return new HashSet<>(flowPool.values());
    }
    */

    /**
     * Gets all {@link Flow} instances witch contain specified {@link Switch} instance id in the path.
     *
     * @param switchId {@link Switch} instance id
     * @return {@link Set} of {@link Flow} instances
     */
    /*
    public Set<Flow> getAffectedBySwitchFlows(String switchId) {
        logger.debug("Get all flows with switch {} in the path", switchId);

        return flowPool.values().stream()
                .filter(flow -> flow.containsSwitchInPath(switchId))
                .collect(Collectors.toSet());

    }
    */

    /**
     * Gets all {@link Flow} instances witch contain specified {@link Isl} instance id in the path.
     *
     * @param islId {@link Isl} instance id
     * @return {@link Set} of {@link Flow} instances
     */
    /*
    public Set<Flow> getAffectedByIslFlows(String islId) {
        logger.debug("Get all flows with isl {} in the path", islId);

        return flowPool.values().stream()
                .filter(flow -> flow.containsIslInPath(islId))
                .collect(Collectors.toSet());
    }
    */

    /*
    public void rerouteFlow(String flowId) {
        return;
    }
    */

    /**
     * Allocates transit vlan ids and cookie.
     *
     * @param flow flow {@link Flow} instance
     */
    /*
    private static void loadResources(Flow flow) {
        Utils.allocateTransitVlan(flow.getForwardVlan());
        Utils.allocateTransitVlan(flow.getReverseVlan());
        Utils.allocateCookie(flow.getCookie());
    }
    */

    /**
     * Allocates transit vlan ids and cookie and updates available bandwidth on specified path.
     *
     * @param flow {@link Flow} instance
     */
    /*
    private void allocateResources(Flow flow, Set<Isl> path) {
        flow.setLastUpdated(Utils.getIsoTimestamp());
        flow.setFlowPath(path);
        flow.setCookie(Utils.allocateCookie());
        flow.setForwardVlan(Utils.allocateTransitVlan());
        flow.setReverseVlan(Utils.allocateTransitVlan());
        flow.setFlowState(FlowState.ALLOCATED);

        pathComputer.updatePathBandwidth(path, flow.getBandwidth());
    }
    */

    /**
     * Deallocates transit vlan ids and cookie and updates available bandwidth on flow path.
     *
     * @param flow {@link Flow} instance
     */
    /*
    private void deallocateResources(Flow flow) {
        Utils.deallocateTransitVlan(flow.getForwardVlan());
        Utils.deallocateTransitVlan(flow.getReverseVlan());
        Utils.deallocateCookie(flow.getCookie());

        pathComputer.updatePathBandwidth(flow.getFlowPath(), -flow.getBandwidth());
    }
    */

    /**
     * Returns intersection between two paths.
     *
     * @param firstPath  first {@link LinkedList} of {@link Isl} instances
     * @param secondPath second {@link LinkedList} of {@link Isl} instances
     * @return intersection {@link Set} of {@link Isl} instances
     */
    public Set<Isl> getPathIntersection(LinkedList<Isl> firstPath, LinkedList<Isl> secondPath) {
        logger.debug("Get path intersection between {} and {}", firstPath, secondPath);

        Set<Isl> intersection = pathComputer.getPathIntersection(firstPath, secondPath);

        logger.debug("Path intersection is {}", intersection);

        return intersection;
    }
}
