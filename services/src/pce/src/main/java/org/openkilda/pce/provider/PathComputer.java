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
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;

import java.io.Serializable;
import java.util.List;

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
     * Gets path between source and destination switch.
     *
     * @param flow {@link Flow} instances
     * @return {@link PathInfoData} instances
     */
    ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow, Strategy strategy) throws UnroutablePathException;

    /**
     * Interact with the PathComputer to get the FlowInfo for all flows.
     *
     * @return a list containing the "key" flow info for all flows.
     */
    List<FlowInfo> getFlowInfo();

}
