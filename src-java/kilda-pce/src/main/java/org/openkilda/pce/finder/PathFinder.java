/* Copyright 2018 Telstra Open Source
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

package org.openkilda.pce.finder;

import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.FindOneDirectionPathResult;
import org.openkilda.pce.model.FindPathResult;
import org.openkilda.pce.model.WeightFunction;

import java.util.List;

/**
 * Represents path finding algorithm over in-memory {@link AvailableNetwork}.
 */
public interface PathFinder {
    /**
     * Find a path from the start to the end switch with min weight.
     *
     * @return a pair of ordered lists that represents the path from start to end, or an empty list if no path found.
     */
    FindPathResult findPathWithMinWeight(AvailableNetwork network,
                                         SwitchId startSwitchId, SwitchId endSwitchId,
                                         WeightFunction weightFunction)
            throws UnroutableFlowException;

    /**
     * Find a path from the start to the end switch with min weight and latency limits.
     *
     * @return a pair of ordered lists that represents the path from start to end, or an empty list if no path found.
     *     Returns backUpPathComputationWayUsed = true if found path has latency greater than maxLatency.
     *     Returns an empty path if the found path has latency greater than latencyLimit.
     */
    FindPathResult findPathWithMinWeightAndLatencyLimits(AvailableNetwork network,
                                                         SwitchId startSwitchId, SwitchId endSwitchId,
                                                         WeightFunction weightFunction,
                                                         long maxLatency, long latencyLimit)
            throws UnroutableFlowException;

    /**
     * Finds a path whose weight is less than maxWeight and as close to maxWeight as possible.
     * If this path is not found, then backUpMaxWeight is used as maxWeight.
     *
     * @return a pair of ordered lists that represents the path from start to end, or an empty list if no path found.
     */
    FindPathResult findPathWithWeightCloseToMaxWeight(AvailableNetwork network,
                                                      SwitchId startSwitchId, SwitchId endSwitchId,
                                                      WeightFunction weightFunction,
                                                      long maxWeight, long backUpMaxWeight)
            throws UnroutableFlowException;

    /**
     * Find the best N paths.
     * N is a number, not greater than count, of all paths that can be found.
     *
     * @param startSwitchId source switchId
     * @param endSwitchId destination switchId
     * @param network available network
     * @param count find no more than this number of paths
     * @param weightFunction use this weight function for the path computation
     * @return a list of the best N paths.
     */
    List<FindOneDirectionPathResult> findNPathsBetweenSwitches(
            AvailableNetwork network, SwitchId startSwitchId, SwitchId endSwitchId,
            int count, WeightFunction weightFunction) throws UnroutableFlowException;

    /**
     * Find the best N paths with max weight restrictions.
     * N is a number, not greater than count, of all paths that can be found.
     *
     * @return a list of the best N paths.
     */
    List<FindOneDirectionPathResult> findNPathsBetweenSwitches(
            AvailableNetwork network, SwitchId startSwitchId, SwitchId endSwitchId, int count,
            WeightFunction weightFunction, long maxWeight, long backUpMaxWeight) throws UnroutableFlowException;
}
