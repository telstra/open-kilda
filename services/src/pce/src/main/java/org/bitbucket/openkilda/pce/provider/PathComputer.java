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

package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;

import com.google.common.graph.MutableNetwork;

import java.io.Serializable;

/**
 * PathComputation interface represent operations on flow path.
 */
public interface PathComputer extends Serializable {
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
     * Checks is path empty.
     *
     * @param path path
     * @return true if path is empty. otherwise false
     */
    default boolean isEmpty(ImmutablePair<PathInfoData, PathInfoData> path) {
        return path.getLeft().getPath().isEmpty() || path.getRight().getPath().isEmpty();
    }

    /**
     * Gets path between source and destination switch.
     *
     * @param flow {@link Flow} instances
     * @return {@link PathInfoData} instances
     */
    ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow);

    /**
     * Gets path between source and destination switch.
     *
     * @param source      source {@link SwitchInfoData} instance
     * @param destination source {@link SwitchInfoData} instance
     * @param bandwidth   available bandwidth
     * @return {@link PathInfoData} instances
     */
    ImmutablePair<PathInfoData, PathInfoData> getPath(SwitchInfoData source, SwitchInfoData destination,
                                                      int bandwidth);

    /**
     * Sets network topology.
     *
     * @param network network topology represented by {@link MutableNetwork} instance
     * @return {@link PathComputer} instance
     */
    PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network);

    /**
     * Initialises path computer.
     */
    void init();
}
