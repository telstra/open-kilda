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

package org.openkilda.pce.provider;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.model.AvailableNetwork;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PathComputation interface represent operations on flow path.
 */
public interface PathComputer extends Serializable {

    /**
     * The Strategy is used for getting a Path - ie what filters to apply.
     * In reality, to provide flexibility, this should most likely be one or more strings.
     */
    enum Strategy {
        HOPS, COST, LATENCY, EXTERNAL
    }

    /**
     * Gets isl weight.
     *
     * @param isl isl instance
     * @return isl weight
     */
    default Long getWeight(IslInfoData isl) {
        return 1L;
    }

    /**
     * Gets path between source and destination switches for specified flow in preloaded network topology.
     *
     * @param flow {@link Flow} instances
     * @param network prepared network where searching will be performed.
     * @return {@link PathInfoData} instances
     */
    FlowPair<PathInfoData, PathInfoData> getPath(Flow flow, AvailableNetwork network, Strategy strategy)
            throws UnroutablePathException, RecoverableException;

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow {@link Flow} instances
     * @return {@link PathInfoData} instances
     */
    FlowPair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy)
            throws UnroutablePathException, RecoverableException;

    /**
     * Interact with the PathComputer to get the FlowInfo for all flows.
     *
     * @return a list containing the "key" flow info for all flows.
     */
    default List<FlowInfo> getFlowInfo() {
        return new ArrayList<>();
    }

    /**
     * Read flows from Neo4j and covert them in our common representation
     * org.openkilda.messaging.model.Flow
     *
     * @return all flow objects stored in neo4j
     */
    default List<Flow> getAllFlows() {
        return new ArrayList<>();
    }

    /**
     * Read a single flow from Neo4j and convert to our common representation {@link Flow}.
     * In reality, a single flow will typically be bi-directional, so just represent as a list.
     *
     * @return the Flow if it exists, null otherwise.
     */
    List<Flow> getFlow(String flowId);

    /*
     * @return all flows (forward and reverse) by id, if exist.
     */
    default List<Flow> getFlows(String flowId) {
        return new ArrayList<>();
    }

    default List<SwitchInfoData> getSwitches() {
        return new ArrayList<>();
    }

    default Optional<SwitchInfoData> getSwitchById(SwitchId id) {
        return Optional.empty();
    }

    default List<IslInfoData> getIsls() {
        return new ArrayList<>();
    }

    /**
     * Loads network and ignores all ISLs with not enough available bandwidth if ignoreBandwidth is false.
     *
     * @param ignoreBandwidth defines if available bandwidth of links should be taken into account for calculations.
     * @param requestedBandwidth links in path should have enough amount of available bandwidth.
     * @return built network.
     */
    AvailableNetwork getAvailableNetwork(boolean ignoreBandwidth, long requestedBandwidth);
}
