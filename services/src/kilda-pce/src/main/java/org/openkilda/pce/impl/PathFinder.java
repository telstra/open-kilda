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

package org.openkilda.pce.impl;

import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.UnroutableFlowException;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Represents path finding algorithm over in-memory {@link AvailableNetwork}.
 */
public interface PathFinder {
    /**
     * Find a path from the start to the end switch.
     *
     * @return an ordered list that represents the path from start to end, or an empty list if no path found.
     */
    Pair<List<Isl>, List<Isl>> findPathInNetwork(AvailableNetwork network,
                                                 SwitchId startSwitchId, SwitchId endSwitchId)
            throws SwitchNotFoundException, UnroutableFlowException;
}
