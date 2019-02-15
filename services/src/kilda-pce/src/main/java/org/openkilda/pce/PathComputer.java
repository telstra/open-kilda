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

import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.pce.AvailableNetworkFactory.BuildStrategy;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;

/**
 * Represents computation operations on flow path.
 */
public interface PathComputer {

    /**
     * Gets path between source and destination switches for specified flow. The path is built over available ISLs
     * only.
     *
     * @param flow the {@link UnidirectionalFlow} instance
     * @return {@link PathPair} instances
     */
    default PathPair getPath(UnidirectionalFlow flow) throws UnroutableFlowException, RecoverableException {
        return getPath(flow, false);
    }

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow the {@link UnidirectionalFlow} instance.
     * @param reuseAllocatedFlowBandwidth whether to reuse allocated bandwidth and existing path of the flow
     *                                    to be a potential new path.
     * @return {@link PathPair} instances
     */
    PathPair getPath(UnidirectionalFlow flow, boolean reuseAllocatedFlowBandwidth)
            throws UnroutableFlowException, RecoverableException;

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow the {@link UnidirectionalFlow} instance.
     * @param reuseAllocatedFlowBandwidth whether to reuse allocated bandwidth and existing path of the flow
     *                                    to be a potential new path.
     * @param buildStrategy  wei
     * @return {@link PathPair} instances
     */
    PathPair getPath(UnidirectionalFlow flow, boolean reuseAllocatedFlowBandwidth, BuildStrategy buildStrategy)
            throws UnroutableFlowException, RecoverableException;
}
