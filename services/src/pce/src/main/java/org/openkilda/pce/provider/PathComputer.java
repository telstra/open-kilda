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

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.model.AvailableNetwork;

import java.io.Serializable;

/**
 * PathComputation interface represent operations on flow path.
 */
public interface PathComputer extends TopologyRepository, Serializable {

    /**
     * The Strategy is used for getting a Path - ie what filters to apply.
     * In reality, to provide flexibility, this should most likely be one or more strings.
     */
    enum Strategy {
        HOPS, COST, LATENCY, EXTERNAL
    }

    /**
     * Gets path between source and destination switches for specified flow in preloaded network topology.
     *
     * @param flow {@link Flow} instances
     * @param network prepared network where searching will be performed.
     * @return {@link PathInfoData} instances
     */
    ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, AvailableNetwork network, Strategy strategy)
            throws UnroutablePathException, RecoverableException;

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow {@link Flow} instances
     * @return {@link PathInfoData} instances
     */
    ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy)
            throws UnroutablePathException, RecoverableException;

    /**
     * Loads network and ignores all ISLs with not enough available bandwidth if ignoreBandwidth is false.
     * @param ignoreBandwidth defines if available bandwidth of links should be taken into account for calculations.
     * @param requestedBandwidth links in path should have enough amount of available bandwidth.
     * @return built network.
     */
    AvailableNetwork getAvailableNetwork(boolean ignoreBandwidth, int requestedBandwidth);
}
