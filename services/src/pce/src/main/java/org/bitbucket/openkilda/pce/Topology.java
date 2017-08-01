package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.messaging.info.event.SwitchEventType;
import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Node;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.storage.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Topology class contains basic operations on network topology.
 */
public class Topology {
    private static final Logger logger = LoggerFactory.getLogger(Topology.class);
    private static final Topology TOPOLOGY = new Topology();
    private static final Map<String, Flow> flowsInReconfiguration = new ConcurrentHashMap<>();

    private static Map<String, Switch> switchPool;
    private static Map<String, Flow> flowPool;
    private static Map<String, Isl> islPool;
    private static Storage storage;

    public static Topology getTopologyManager(Storage stateStorage) {
        storage = stateStorage;

        if (switchPool == null) {
            logger.info("Load Switch Pool");
            List<Switch> switchList = storage.dumpSwitches();

            logger.debug("Switch List: {}", switchList);
            switchPool = switchList.stream().collect(Collectors.toMap(Switch::getSwitchId, item -> item));
        }

        if (islPool == null) {
            logger.info("Load Isl Pool");
            List<Isl> islList = storage.dumpIsls();

            logger.debug("Isl List: {}", islList);
            islPool = islList.stream().collect(Collectors.toMap(Isl::getId, item -> item));
        }

        if (flowPool == null) {
            logger.info("Load Flow Pool");
            List<Flow> flowList = storage.dumpFlows();

            logger.debug("Flow List: {}", flowList);
            flowPool = flowList.stream().collect(Collectors.toMap(Flow::getFlowId, item -> item));
        }

        return TOPOLOGY;
    }

    public Isl createIsl(Isl isl) {
        String islId = isl.getId();
        logger.debug("Create an isl: isl_id={}, isl={}", islId, isl);
        storage.createIsl(isl);
        return islPool.put(islId, isl);
    }

    public Isl updateIsl(String islId, Isl isl) {
        logger.debug("Update an isl: isl_id={}, isl={}", islId, isl);
        storage.updateIsl(islId, isl);
        return islPool.put(islId, isl);
    }

    public Isl deleteIsl(String islId) {
        logger.debug("Delete an isl: isl_id={}", islId);
        storage.deleteIsl(islId);
        return islPool.remove(islId);
    }

    public Isl getIsl(String islId) {
        logger.debug("Get an isl: isl_id={}", islId);
        return islPool.get(islId);
    }

    public List<Isl> dumpIsls() {
        logger.debug("Get all isls");
        return new ArrayList<>(islPool.values());
    }

    public Set<Isl> getIslsWithSource(String switchId) {
        logger.debug("Get all isls with source: switch_id={}", switchId);
        return islPool.values().stream()
                .filter(isl -> isl.getSourceSwitch().equals(switchId))
                .collect(Collectors.toSet());
    }

    public Set<Isl> getIslsWithDestination(String switchId) {
        logger.debug("Get all isls with destination: switch_id={}", switchId);
        return islPool.values().stream()
                .filter(isl -> isl.getDestinationSwitch().equals(switchId))
                .collect(Collectors.toSet());
    }

    public Switch createSwitch(Switch newSwitch) {
        String switchId = newSwitch.getSwitchId();
        logger.debug("Create a switch: switch_id={}, switch={}", switchId, newSwitch);
        storage.createSwitch(newSwitch);
        return switchPool.put(switchId, newSwitch);
    }

    public Switch updateSwitch(String switchId, Switch newSwitch) {
        logger.debug("Update a switch: switch_id={}, switch={}", switchId, newSwitch);
        storage.updateSwitch(switchId, newSwitch);
        return switchPool.put(switchId, newSwitch);
    }

    public Switch deleteSwitch(String switchId) {
        logger.debug("Delete a switch: switch_id={}", switchId);
        storage.deleteSwitch(switchId);
        return switchPool.remove(switchId);
    }

    public Switch getSwitch(String switchId) {
        logger.debug("Get a switch: switch_id={}", switchId);
        return switchPool.get(switchId);
    }

    public List<Switch> dumpSwitches() {
        logger.debug("Get all switches");
        return new ArrayList<>(switchPool.values());
    }

    public Set<Switch> getStateSwitches(SwitchEventType state) {
        logger.debug("Get all switches in {} state", state);
        return switchPool.values().stream()
                .filter(sw -> sw.getState() == state)
                .collect(Collectors.toSet());
    }

    public Set<Switch> getControllerSwitches(String controller) {
        logger.debug("Get all switches connected to {} controller", controller);
        return switchPool.values().stream()
                .filter(sw -> sw.getController().equals(controller))
                .collect(Collectors.toSet());
    }

    public Set<Switch> getDirectlyConnectedSwitches(String switchId) {
        logger.debug("Get all switches directly connected to {} switch ", switchId);
        return islPool.values().stream()
                .filter(isl -> isl.getSourceSwitch().equals(switchId))
                .map(isl -> switchPool.get(isl.getDestinationSwitch()))
                .collect(Collectors.toSet());
    }

    public Flow createFlow(Flow flow) {
        String flowId = flow.getFlowId();
        logger.debug("Create a flow: flow_id={}, flow={}", flowId, flow);
        storage.createFlow(flow);
        return flowPool.put(flowId, flow);
    }

    public Flow deleteFlow(String flowId) {
        logger.debug("Delete a flow: flow_id={}", flowId);
        storage.deleteFlow(flowId);
        return flowPool.remove(flowId);
    }

    public Flow getFlow(String flowId) {
        logger.debug("Get a flow: flow_id={}", flowId);
        return flowPool.get(flowId);
    }

    public Flow updateFlow(String flowId, Flow flow) {
        logger.debug("Update a flow: flow_id={}, flow={}", flowId, flow);
        storage.updateFlow(flowId, flow);
        return flowPool.put(flowId, flow);
    }

    public List<Flow> dumpFlows() {
        logger.debug("Get all flows");
        return new ArrayList<>(flowPool.values());
    }

    public Set<Flow> getAffectedFlows(Node node) {
        logger.debug("Get all flows with node {} in the path", node);
        return flowPool.values().stream()
                .filter(flow -> flow.getFlowPath().contains(node))
                .collect(Collectors.toSet());
    }

    public Set<Node> getSwitchPathBetweenSwitches(String srcSwitch, String dstSwitch) {
        logger.debug("Get switch path between source switch {} and destination switch {}", srcSwitch, dstSwitch);
        return new HashSet<>(storage.getSwitchPath(srcSwitch, dstSwitch));
    }

    public Set<Node> getIslPathBetweenSwitches(String srcSwitch, String dstSwitch) {
        logger.debug("Get isl path between source switch {} and destination switch {}", srcSwitch, dstSwitch);
        return new HashSet<>(storage.getIslPath(srcSwitch, dstSwitch));
    }

    public void rerouteFlow(String flowId) {
        return;
    }
}
