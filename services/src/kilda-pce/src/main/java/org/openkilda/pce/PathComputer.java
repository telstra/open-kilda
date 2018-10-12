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

package org.openkilda.pce;

import org.openkilda.model.Flow;

/**
 * PathComputation interface represent operations on flow path.
 */
public interface PathComputer {

    /**
     * The Strategy is used for getting a Path - ie what filters to apply.
     * In reality, to provide flexibility, this should most likely be one or more strings.
     */
    enum Strategy {
        HOPS, COST, LATENCY, EXTERNAL
    }

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow     the {@link Flow} instance
     * @param strategy the path find strategy.
     * @return {@link PathPair} instances
     */
    default PathPair getPath(Flow flow, Strategy strategy)
            throws UnroutableFlowException, RecoverableException {
        return getPath(flow, strategy, false);
    }

    /**
     * Gets path between source and destination switch for specified flow.
     *
     * @param flow              the {@link Flow} instance.
     * @param strategy          the path find strategy.
     * @param allowSameFlowPath whether to allow the existing flow path to be a potential new path or not.
     * @return {@link PathPair} instances
     */
    PathPair getPath(Flow flow, Strategy strategy, boolean allowSameFlowPath)
            throws UnroutableFlowException, RecoverableException;
}
