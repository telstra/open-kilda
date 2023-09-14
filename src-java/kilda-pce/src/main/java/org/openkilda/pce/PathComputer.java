/* Copyright 2021 Telstra Open Source
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

package org.openkilda.pce;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.impl.RequestedPath;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents computation operations on flow paths.
 */
public interface PathComputer {

    /**
     * Gets a path between source and destination switches for a specified flow. The path is built over available ISLs
     * only.
     *
     * @param flow the {@link Flow} instance
     * @return {@link GetPathsResult} instance
     */
    default GetPathsResult getPath(Flow flow) throws UnroutableFlowException, RecoverableException {
        return getPath(flow, Collections.emptyList(), false);
    }

    /**
     * Gets a path between source and destination switches for a specified flow.
     *
     * @param flow the {@link Flow} instance.
     * @param reusePathsResources allow already allocated path resources to be used in the new path computation.
     * @param isProtected true if it is required to find a protected path.
     * @return {@link GetPathsResult} instance
     */
    GetPathsResult getPath(Flow flow, Collection<PathId> reusePathsResources, boolean isProtected)
            throws UnroutableFlowException, RecoverableException;

    /**
     * Gets a path in a given AvailableNetwork fulfilling the requirements of the requestedPath.
     * This method is useful when it is required to find a path in a subset of the entire network.
     * @param network the network in which it is required to find a path
     * @param requestedPath parameters of the path
     * @param isProtected true if it is required to find a protected path.
     * @return instance
     */
    GetPathsResult getPath(AvailableNetwork network, RequestedPath requestedPath, boolean isProtected)
            throws UnroutableFlowException;

    /**
     * Gets an HA-path for the specified HA-flow.
     *
     * @param haFlow the {@link HaFlow} instance.
     * @param isProtected true if it is required to find a protected path.
     * @return {@link GetPathsResult} instance
     */
    default GetHaPathsResult getHaPath(HaFlow haFlow, boolean isProtected)
            throws UnroutableFlowException, RecoverableException {
        return getHaPath(haFlow, Collections.emptyList(), isProtected);
    }

    /**
     * Gets an HA-path for the specified HA-flow.
     *
     * @param haFlow the {@link HaFlow} instance.
     * @param reuseSubPathsResources allow already allocated resources to be used in the path computation.
     * @return {@link GetHaPathsResult} instance
     */
    GetHaPathsResult getHaPath(HaFlow haFlow, Collection<PathId> reuseSubPathsResources, boolean isProtected)
            throws UnroutableFlowException, RecoverableException;

    /**
     * Calculates a protected path based on the given parameters and configuration.
     * @param flow This method calculates a protected path to the main path of this flow.
     * @param reusePathsResources include these resources as if they are not allocated
     * @return GetPathResult containing a protected path or an empty path with fail reasons if it is not possible
     *      to calculate the path.
     */
    GetPathsResult getProtectedPath(Flow flow, Collection<PathId> reusePathsResources);

    /**
     * Gets the best N paths. N is a number, not greater than the count param, of all paths that can be found.
     *
     * @param srcSwitch source switchId
     * @param dstSwitch destination switchId
     * @param count calculates no more than this number of paths
     * @param flowEncapsulationType target encapsulation type
     * @param pathComputationStrategy depending on this strategy, different weight functions are used
     *                               to determine the best path
     * @param maxLatency max latency
     * @param maxLatencyTier2 max latency tier2
     * @return a list of the best N paths ordered from best to worst.
     */
    List<Path> getNPaths(SwitchId srcSwitch, SwitchId dstSwitch, int count,
                         FlowEncapsulationType flowEncapsulationType, PathComputationStrategy pathComputationStrategy,
                         Duration maxLatency, Duration maxLatencyTier2)
            throws RecoverableException, UnroutableFlowException;

    /**
     * Finds the Y-point from the provided flow paths.
     */
    SwitchId getIntersectionPoint(SwitchId sharedSwitchId, FlowPath... flowPaths);
}
