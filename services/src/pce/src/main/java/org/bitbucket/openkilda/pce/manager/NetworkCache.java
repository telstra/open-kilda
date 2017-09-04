package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchState;

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

public class NetworkCache extends BaseCache {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NetworkCache.class);

    /**
     * Network cache.
     */
    private final MutableNetwork<SwitchInfoData, IslInfoData> network = NetworkBuilder
            .directed()
            .allowsSelfLoops(false)
            .allowsParallelEdges(true)
            .build();

    /**
     * Switches pool.
     */
    private final Map<String, SwitchInfoData> switchPool = new ConcurrentHashMap<>();

    /**
     * Isl pool.
     */
    private final Map<String, IslInfoData> islPool = new ConcurrentHashMap<>();

    /**
     * Fills cache.
     *
     * @param switches {@link Set} of {@link SwitchInfoData} instances
     * @param isls     {@link Set} of {@link IslInfoData} instances
     */
    public void load(Set<SwitchInfoData> switches, Set<IslInfoData> isls) {
        logger.debug("Switches: {}", switches);
        switches.forEach(this::createSwitchCache);

        logger.debug("Isls: {}", isls);
        isls.forEach(isl -> createIslCache(isl.getId(), isl));
    }

    /**
     * Gets all {@link IslInfoData} instances which start node is specified {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link Set} of {@link IslInfoData} instances
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exists
     */
    public Set<IslInfoData> getIslsBySource(String switchId) {
        logger.debug("Get all isls by source switch {}", switchId);

        SwitchInfoData startNode = getSwitchCache(switchId);

        return network.outEdges(startNode);
    }

    /**
     * Gets all {@link IslInfoData} instances which end node is specified {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link Set} of {@link IslInfoData} instances
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exists
     */
    public Set<IslInfoData> getIslsByDestination(String switchId) {
        logger.debug("Get all isls by destination switch {}", switchId);

        SwitchInfoData endNode = getSwitchCache(switchId);

        return network.inEdges(endNode);
    }

    /**
     * Gets all {@link IslInfoData} instances which start or end node is specified {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link Set} of {@link IslInfoData} instances
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exists
     */
    public Set<IslInfoData> getIslsBySwitch(String switchId) throws IllegalArgumentException {
        logger.debug("Get all isls incident switch {}", switchId);

        SwitchInfoData node = getSwitchCache(switchId);

        return network.incidentEdges(node);
    }

    /**
     * Gets all {@link SwitchInfoData} instances in specified {@link SwitchState} state.
     *
     * @param state {@link SwitchState} state
     * @return {@link Set} of {@link SwitchInfoData} instances
     */
    public Set<SwitchInfoData> getStateSwitches(SwitchState state) {
        logger.debug("Get all switches in {} state", state);

        return network.nodes().stream()
                .filter(sw -> sw.getState() == state)
                .collect(Collectors.toSet());
    }

    /**
     * Gets all {@link SwitchInfoData} instances with specified controller ip address.
     *
     * @param controller controller ip address
     * @return {@link Set} of {@link SwitchInfoData} instances
     */
    public Set<SwitchInfoData> getControllerSwitches(String controller) {
        logger.debug("Get all switches connected to {} controller", controller);

        return network.nodes().stream()
                .filter(sw -> sw.getController().equals(controller))
                .collect(Collectors.toSet());
    }

    /**
     * Gets all {@link SwitchInfoData} instances directly connected to specified.
     *
     * @param switchId switch id
     * @return {@link Set} of {@link SwitchInfoData} instances
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public Set<SwitchInfoData> getDirectlyConnectedSwitches(String switchId) throws IllegalArgumentException {
        logger.debug("Get all switches directly connected to {} switch ", switchId);

        SwitchInfoData node = getSwitchCache(switchId);

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
     * Gets {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link SwitchInfoData} instance with specified {@link SwitchInfoData} instance id
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData getSwitchCache(String switchId) throws IllegalArgumentException {
        logger.debug("Get {} switch", switchId);

        SwitchInfoData node = switchPool.get(switchId);
        if (node == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", switchId));
        }

        return node;
    }

    /**
     * Creates {@link SwitchInfoData} instance.
     *
     * @param newSwitch {@link SwitchInfoData} instance
     * @return created {@link SwitchInfoData} instance
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id already exists
     */
    public SwitchInfoData createSwitchCache(SwitchInfoData newSwitch) throws IllegalArgumentException {
        String switchId = newSwitch.getSwitchId();

        logger.debug("Create {} switch with {} parameters", switchId, newSwitch);

        SwitchInfoData oldSwitch = switchPool.get(switchId);
        if (oldSwitch != null) {
            throw new IllegalArgumentException(String.format("Switch %s already exists", switchId));
        }

        network.addNode(newSwitch);
        switchPool.put(switchId, newSwitch);

        return newSwitch;
    }

    /**
     * Updates {@link SwitchInfoData} instance.
     *
     * @param switchId  {@link SwitchInfoData} instance id
     * @param newSwitch {@link SwitchInfoData} instance
     * @return {@link SwitchInfoData} instance before update
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData updateSwitchCache(String switchId, SwitchInfoData newSwitch) throws IllegalArgumentException {
        logger.debug("Update {} switch with {} parameters", switchId, newSwitch);

        SwitchInfoData oldSwitch = switchPool.remove(switchId);
        if (oldSwitch == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", switchId));
        }

        network.removeNode(oldSwitch);
        network.addNode(newSwitch);
        switchPool.put(switchId, newSwitch);

        return newSwitch;
    }

    /**
     * Deletes {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return removed {@link SwitchInfoData} instance
     * @throws IllegalArgumentException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData deleteSwitchCache(String switchId) throws IllegalArgumentException {
        logger.debug("Delete {} switch", switchId);

        SwitchInfoData node = switchPool.remove(switchId);
        if (node == null) {
            throw new IllegalArgumentException(String.format("Switch %s not found", switchId));
        }

        network.removeNode(node);

        return node;
    }

    /**
     * Gets all {@link SwitchInfoData} instances.
     *
     * @return {@link Set} of {@link SwitchInfoData} instances
     */
    public Set<SwitchInfoData> dumpSwitchesCache() {
        logger.debug("Get all switches");

        return new HashSet<>(network.nodes());
    }

    /**
     * Checks if switch pool contains {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return true if switch pool contains {@link SwitchInfoData} instance
     */
    public boolean cacheContainsSwitch(String switchId) {
        logger.debug("Is switch {} in cache", switchId);

        return switchPool.containsKey(switchId);
    }

    /**
     * Get {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @return {@link IslInfoData} instance with specified {@link IslInfoData} instance id
     * @throws IllegalArgumentException if {@link IslInfoData} instance with specified id does not exist
     */
    public IslInfoData getIslCache(String islId) throws IllegalArgumentException {
        logger.debug("Get {} isl", islId);

        IslInfoData isl = islPool.get(islId);
        if (isl == null) {
            throw new IllegalArgumentException(String.format("Isl %s not found", islId));
        }

        return islPool.get(islId);
    }

    /**
     * Creates {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @param isl   {@link IslInfoData} instance
     * @return {@link IslInfoData} instance previously associated with {@link IslInfoData} instance id or null otherwise
     * @throws IllegalArgumentException if {@link SwitchInfoData} related to {@link IslInfoData} instance do not exist
     */
    public IslInfoData createIslCache(String islId, IslInfoData isl) throws IllegalArgumentException {
        logger.debug("Create {} isl with {} parameters", islId, isl);

        EndpointPair<SwitchInfoData> nodes = getIslSwitches(isl);
        network.addEdge(nodes.source(), nodes.target(), isl);

        return islPool.put(islId, isl);
    }

    /**
     * Updates {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @param isl   new {@link IslInfoData} instance
     * @return {@link IslInfoData} instance previously associated with {@link IslInfoData} instance id or null otherwise
     * @throws IllegalArgumentException if {@link SwitchInfoData} related to {@link IslInfoData} instance do not exist
     */
    public IslInfoData updateIslCache(String islId, IslInfoData isl) throws IllegalArgumentException {
        logger.debug("Update {} isl with {} parameters", islId, isl);

        EndpointPair<SwitchInfoData> nodes = getIslSwitches(isl);
        network.removeEdge(islPool.get(islId));
        network.addEdge(nodes.source(), nodes.target(), isl);

        return islPool.put(islId, isl);
    }

    /**
     * Deletes {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @return removed {@link IslInfoData} instance
     * @throws IllegalArgumentException if {@link IslInfoData} instance with specified id does not exist
     */
    public IslInfoData deleteIslCache(String islId) throws IllegalArgumentException {
        logger.debug("Delete {} isl", islId);

        IslInfoData isl = islPool.remove(islId);
        if (isl == null) {
            throw new IllegalArgumentException(String.format("Isl %s not found", islId));
        }

        network.removeEdge(isl);

        return isl;
    }

    /**
     * Gets all {@link IslInfoData} instances.
     *
     * @return {@link Set} of {@link IslInfoData} instances
     */
    public Set<IslInfoData> dumpIslsCache() {
        logger.debug("Get all isls");

        return new HashSet<>(network.edges());
    }

    /**
     * Checks if isl pool contains {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @return true if isl pool contains {@link IslInfoData} instance
     */
    public boolean cacheContainsIsl(String islId) {
        logger.debug("Is isl {} in cache", islId);

        return islPool.containsKey(islId);
    }

    /**
     * Gets internal network.
     *
     * @return internal network
     */
    protected MutableNetwork<SwitchInfoData, IslInfoData> getNetwork() {
        return network;
    }

    /**
     * Gets {@link SwitchInfoData} instances which are incident nodes for specified {@link IslInfoData} instance.
     *
     * @param isl {@link IslInfoData} instance
     * @return {@link EndpointPair} of {@link SwitchInfoData} instances
     * @throws IllegalArgumentException if {@link SwitchInfoData} instances for {@link IslInfoData} instance do not
     *                                  exist
     */
    private EndpointPair<SwitchInfoData> getIslSwitches(IslInfoData isl) throws IllegalArgumentException {
        String srcSwitch = isl.getPath().get(0).getSwitchId();
        if (srcSwitch == null) {
            throw new IllegalArgumentException("Source switch not specified");
        }

        SwitchInfoData startNode = getSwitchCache(srcSwitch);

        String dstSwitch = isl.getPath().get(1).getSwitchId();
        if (dstSwitch == null) {
            throw new IllegalArgumentException("Destination switch not specified");
        }

        SwitchInfoData endNode = getSwitchCache(dstSwitch);

        return EndpointPair.ordered(startNode, endNode);
    }
}
