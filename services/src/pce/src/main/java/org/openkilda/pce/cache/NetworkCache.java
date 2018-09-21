/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.pce.cache;

import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;

import com.google.common.annotations.VisibleForTesting;
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
import java.util.stream.Collectors;

public class NetworkCache extends Cache {
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
    private final Map<SwitchId, SwitchInfoData> switchPool = new ConcurrentHashMap<>();

    /**
     * SimpleIsl pool.
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
        switches.forEach(this::createSwitch);

        logger.debug("Isls: {}", isls);
        isls.forEach(this::createIsl);
    }

    /**
     * Gets all {@link IslInfoData} instances which start node is specified {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link Set} of {@link IslInfoData} instances
     * @throws CacheException if {@link SwitchInfoData} instance with specified id does not exists
     */
    public Set<IslInfoData> getIslsBySource(SwitchId switchId) {
        logger.debug("Get all isls by source switch {}", switchId);

        SwitchInfoData startNode = getSwitch(switchId);

        return network.outEdges(startNode);
    }

    /**
     * Gets all {@link IslInfoData} instances which end node is specified {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link Set} of {@link IslInfoData} instances
     * @throws CacheException if {@link SwitchInfoData} instance with specified id does not exists
     */
    public Set<IslInfoData> getIslsByDestination(SwitchId switchId) {
        logger.debug("Get all isls by destination switch {}", switchId);

        SwitchInfoData endNode = getSwitch(switchId);

        return network.inEdges(endNode);
    }

    /**
     * Gets all {@link IslInfoData} instances which start or end node is specified {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return {@link Set} of {@link IslInfoData} instances
     * @throws CacheException if {@link SwitchInfoData} instance with specified id does not exists
     */
    public Set<IslInfoData> getIslsBySwitch(SwitchId switchId) throws CacheException {
        logger.debug("Get all isls incident switch {}", switchId);

        SwitchInfoData node = getSwitch(switchId);

        return network.incidentEdges(node);
    }

    /**
     * Gets all {@link SwitchInfoData} instances in specified {@link SwitchChangeType} state.
     *
     * @param state {@link SwitchChangeType} state
     * @return {@link Set} of {@link SwitchInfoData} instances
     */
    public Set<SwitchInfoData> getStateSwitches(SwitchChangeType state) {
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
     * @throws CacheException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public Set<SwitchInfoData> getDirectlyConnectedSwitches(SwitchId switchId) throws CacheException {
        logger.debug("Get all switches directly connected to {} switch ", switchId);

        SwitchInfoData node = getSwitch(switchId);

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
     * @throws CacheException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData getSwitch(SwitchId switchId) throws CacheException {
        logger.debug("Get {} switch", switchId);

        SwitchInfoData node = switchPool.get(switchId);
        if (node == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not get switch",
                    String.format("SimpleSwitch %s not found", switchId));
        }

        return node;
    }

    /**
     * Creates {@link SwitchInfoData} instance.
     *
     * @param newSwitch {@link SwitchInfoData} instance
     * @return created {@link SwitchInfoData} instance
     * @throws CacheException if {@link SwitchInfoData} instance with specified id already exists
     */
    public SwitchInfoData createSwitch(SwitchInfoData newSwitch) throws CacheException {
        SwitchId switchId = newSwitch.getSwitchId();

        logger.debug("Create {} switch with {} parameters", switchId, newSwitch);

        SwitchInfoData oldSwitch = switchPool.get(switchId);
        if (oldSwitch != null) {
            throw new CacheException(ErrorType.ALREADY_EXISTS, "Can not create switch",
                    String.format("SimpleSwitch %s already exists", switchId));
        }

        newSwitch.setCreatedInCacheNow();

        network.addNode(newSwitch);
        switchPool.put(switchId, newSwitch);

        return newSwitch;
    }

    /**
     * Updates {@link SwitchInfoData} instance.
     *
     * @param newSwitch {@link SwitchInfoData} instance
     * @return {@link SwitchInfoData} instance before update
     * @throws CacheException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData updateSwitch(SwitchInfoData newSwitch) throws CacheException {
        SwitchId switchId = newSwitch.getSwitchId();
        logger.debug("Update {} switch with {} parameters", switchId, newSwitch);

        SwitchInfoData oldSwitch = switchPool.remove(switchId);
        if (oldSwitch == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not update switch",
                    String.format("SimpleSwitch %s not found", switchId));
        }

        newSwitch.copyTimeTag(oldSwitch);
        newSwitch.setUpdatedInCacheNow();

        network.removeNode(oldSwitch);
        network.addNode(newSwitch);
        switchPool.put(switchId, newSwitch);

        return newSwitch;
    }

    /**
     * Creates or updates {@link SwitchInfoData} instance.
     *
     * @param newSwitch {@link SwitchInfoData} instance
     * @return created {@link SwitchInfoData} instance
     * @throws CacheException if {@link SwitchInfoData} instance with specified id already exists
     */
    public SwitchInfoData createOrUpdateSwitch(SwitchInfoData newSwitch) {
        logger.debug("Create Or Update {} switch with {} parameters", newSwitch.getSwitchId(), newSwitch);

        if (newSwitch ==  null) {
            throw new IllegalArgumentException("SimpleSwitch can't be null in createOrUpdateSwitch");
        }

        if (cacheContainsSwitch(newSwitch.getSwitchId())) {
            return updateSwitch(newSwitch);
        } else {
            return createSwitch(newSwitch);
        }
    }

    /**
     * Deletes {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return removed {@link SwitchInfoData} instance
     * @throws CacheException if {@link SwitchInfoData} instance with specified id does not exist
     */
    public SwitchInfoData deleteSwitch(SwitchId switchId) throws CacheException {
        logger.debug("Delete {} switch", switchId);

        SwitchInfoData node = switchPool.remove(switchId);
        if (node == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not delete switch",
                    String.format("SimpleSwitch %s not found", switchId));
        }

        network.removeNode(node);

        return node;
    }

    /**
     * Gets all {@link SwitchInfoData} instances.
     *
     * @return {@link Set} of {@link SwitchInfoData} instances
     */
    public Set<SwitchInfoData> dumpSwitches() {
        logger.debug("Get all switches");

        return new HashSet<>(network.nodes());
    }

    /**
     * Checks if switch pool contains {@link SwitchInfoData} instance.
     *
     * @param switchId {@link SwitchInfoData} instance id
     * @return true if switch pool contains {@link SwitchInfoData} instance
     */
    public boolean cacheContainsSwitch(SwitchId switchId) {
        logger.debug("Is switch {} in cache", switchId);

        return switchPool.containsKey(switchId);
    }

    /**
     * Checks if switch in operational state.
     *
     * @param switchId switch id
     * @return true if switch in operational state, otherwise false
     */
    public boolean switchIsOperable(SwitchId switchId) {
        if (cacheContainsSwitch(switchId)) {
            SwitchChangeType switchState = switchPool.get(switchId).getState();
            return SwitchChangeType.ADDED == switchState || SwitchChangeType.ACTIVATED == switchState;
        }
        return false;
    }

    /**
     * Get {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @return {@link IslInfoData} instance with specified {@link IslInfoData} instance id
     * @throws CacheException if {@link IslInfoData} instance with specified id does not exist
     */
    public IslInfoData getIsl(String islId) throws CacheException {
        logger.debug("Get {} isl", islId);

        IslInfoData isl = islPool.get(islId);
        if (isl == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not get isl",
                    String.format("SimpleIsl %s not found", islId));
        }

        return islPool.get(islId);
    }

    /**
     * Creates {@link IslInfoData} instance.
     *
     * @param isl {@link IslInfoData} instance
     * @return {@link IslInfoData} instance previously associated with {@link IslInfoData} instance id or null otherwise
     * @throws CacheException if {@link SwitchInfoData} related to {@link IslInfoData} instance do not exist
     */
    public IslInfoData createIsl(IslInfoData isl) throws CacheException {
        String islId = isl.getId();
        logger.debug("Create {} isl with {} parameters", islId, isl);

        isl.setCreatedInCacheNow();

        EndpointPair<SwitchInfoData> nodes = getIslSwitches(isl);
        network.addEdge(nodes.source(), nodes.target(), isl);

        return islPool.put(islId, isl);
    }

    /**
     * Updates {@link IslInfoData} instance.
     *
     * @param isl new {@link IslInfoData} instance
     * @return {@link IslInfoData} instance previously associated with {@link IslInfoData} instance id or null otherwise
     * @throws CacheException if {@link SwitchInfoData} related to {@link IslInfoData} instance do not exist
     */
    public IslInfoData updateIsl(IslInfoData isl) throws CacheException {
        String islId = isl.getId();
        logger.debug("Update {} isl with {} parameters", islId, isl);

        IslInfoData oldIsl = islPool.get(islId);
        network.removeEdge(oldIsl);

        isl.copyTimeTag(oldIsl);
        isl.setUpdatedInCacheNow();

        EndpointPair<SwitchInfoData> nodes = getIslSwitches(isl);
        network.addEdge(nodes.source(), nodes.target(), isl);

        return islPool.put(islId, isl);
    }

    /**
     * Creates {@link IslInfoData} instance.
     *
     * @param isl {@link IslInfoData} instance
     * @return {@link IslInfoData} instance previously associated with {@link IslInfoData} instance id or null otherwise
     * @throws CacheException if {@link SwitchInfoData} related to {@link IslInfoData} instance do not exist
     */
    public IslInfoData createOrUpdateIsl(IslInfoData isl) {
        logger.debug("Create or Update isl with {} parameters", isl);

        if (isl == null) {
            throw new IllegalArgumentException("ISL can't be null in createOrUpdateIsl");
        }

        if (cacheContainsIsl(isl.getId())) {
            return updateIsl(isl);
        } else {
            return createIsl(isl);
        }
    }

    /**
     * Deletes {@link IslInfoData} instance.
     *
     * @param islId {@link IslInfoData} instance id
     * @return removed {@link IslInfoData} instance
     * @throws CacheException if {@link IslInfoData} instance with specified id does not exist
     */
    public IslInfoData deleteIsl(String islId) throws CacheException {
        logger.debug("Delete {} isl", islId);

        IslInfoData isl = islPool.remove(islId);
        if (isl == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not delete isl",
                    String.format("SimpleIsl %s not found", islId));
        }

        network.removeEdge(isl);

        return isl;
    }

    /**
     * Gets all {@link IslInfoData} instances.
     *
     * @return {@link Set} of {@link IslInfoData} instances
     */
    public Set<IslInfoData> dumpIsls() {
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
    @VisibleForTesting
    MutableNetwork<SwitchInfoData, IslInfoData> getNetwork() {
        return network;
    }

    /**
     * Gets {@link SwitchInfoData} instances which are incident nodes for specified {@link IslInfoData} instance.
     *
     * @param isl {@link IslInfoData} instance
     * @return {@link EndpointPair} of {@link SwitchInfoData} instances
     * @throws CacheException if {@link SwitchInfoData} instances for {@link IslInfoData} instance do not exist
     */
    private EndpointPair<SwitchInfoData> getIslSwitches(IslInfoData isl) throws CacheException {
        SwitchId srcSwitch = isl.getSource().getSwitchId();
        if (srcSwitch == null) {
            throw new CacheException(ErrorType.PARAMETERS_INVALID, "Can not get isl nodes",
                    "Source switch not specified");
        }

        SwitchInfoData startNode = getSwitch(srcSwitch);

        SwitchId dstSwitch = isl.getDestination().getSwitchId();
        if (dstSwitch == null) {
            throw new CacheException(ErrorType.PARAMETERS_INVALID, "Can not get isl nodes",
                    "Destination switch not specified");
        }

        SwitchInfoData endNode = getSwitch(dstSwitch);

        return EndpointPair.ordered(startNode, endNode);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("switches", switchPool)
                .add("isls", islPool)
                .toString();
    }
}
