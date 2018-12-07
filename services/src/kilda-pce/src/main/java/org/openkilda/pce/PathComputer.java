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

package org.openkilda.pce;

import org.openkilda.model.Flow;
import org.openkilda.pce.impl.AvailableNetwork;

/**
 * Represents computation operations on flow path.
 */
public interface PathComputer {

    /**
     * Gets path between source and destination switches for specified flow. The path is built over available ISLs
     * only.
     *
     * @param flow the {@link Flow} instance
     * @return {@link PathPair} instances
     */
    default PathPair getPath(Flow flow) throws UnroutableFlowException, RecoverableException {
        return getPath(flow, false);
    }

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow the {@link Flow} instance.
     * @param reuseAllocatedFlowBandwidth whether to reuse allocated bandwidth and existing path of the flow
     *                                    to be a potential new path.
     * @return {@link PathPair} instances
     */
    default PathPair getPath(Flow flow, boolean reuseAllocatedFlowBandwidth)
            throws UnroutableFlowException, RecoverableException {
        return getPathFromAvailableNetwork(buildNetwork(flow, reuseAllocatedFlowBandwidth), flow);
    }

    /**
     * Build {@code AvailableNetwork} graph view.
     *
     * @param flow              the {@link Flow} instance.
     * @param allowSameFlowPath whether to allow the existing flow path to be a potential new path or not.
     * @return {@link AvailableNetwork} instance
     * @throws RecoverableException if exception occurs in DAO layer
     */
    AvailableNetwork buildNetwork(Flow flow, boolean allowSameFlowPath) throws RecoverableException;

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param network           the {@link AvailableNetwork} graph view.
     * @param flow              the {@link Flow} instance.
     * @return {@link PathPair} instances
     * @throws UnroutableFlowException if path not found.
     */
    PathPair getPathFromAvailableNetwork(AvailableNetwork network, Flow flow) throws UnroutableFlowException;
}
