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
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindPathResult;
import org.openkilda.pce.model.WeightFunction;

import java.util.List;

/**
 * Represents path finding algorithm over in-memory {@link AvailableNetwork}.
 */
public interface PathFinder {
    /**
     * Find a path from the start to the end switch.
     *
     * @return a pair of ordered lists that represents the path from start to end, or an empty list if no path found.
     */
    FindPathResult findPathInNetwork(AvailableNetwork network,
                                     SwitchId startSwitchId, SwitchId endSwitchId,
                                     WeightFunction weightFunction)
            throws UnroutableFlowException;

    /**
     * Finds a path whose weight is less than maxWeight and as close to maxWeight as possible.
     * If this path is not found, then backUpMaxWeight is used as maxWeight.
     *
     * @return a pair of ordered lists that represents the path from start to end, or an empty list if no path found.
     */
    FindPathResult findPathInNetwork(AvailableNetwork network,
                                     SwitchId startSwitchId, SwitchId endSwitchId,
                                     WeightFunction weightFunction, long maxWeight, long backUpMaxWeight)
            throws UnroutableFlowException;

    /**
     * Find N (or less) best paths.
     *
     * @return an list of N (or less) best paths.
     */
    List<List<Edge>> findNPathsBetweenSwitches(AvailableNetwork network, SwitchId startSwitchId, SwitchId endSwitchId,
                                               int count, WeightFunction weightFunction) throws UnroutableFlowException;

    /**
     * Find N (or less) best paths wih max weight restrictions.
     *
     * @return an list of N (or less) best paths.
     */
    List<List<Edge>> findNPathsBetweenSwitches(
            AvailableNetwork network, SwitchId startSwitchId, SwitchId endSwitchId, int count,
            WeightFunction weightFunction, long maxWeight, long backUpMaxWeight) throws UnroutableFlowException;
}
