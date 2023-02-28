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

package org.openkilda.northbound.service;

import org.openkilda.messaging.payload.network.PathsDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service to handle network requests.
 */
public interface NetworkService {

    /**
     * Gets paths between two switches.
     * @param srcSwitch a source switch ID
     * @param dstSwitch a destination switch ID
     * @param encapsulationType include only switches that support this encapsulation type
     * @param pathComputationStrategy use this path computation strategy to find paths
     * @param maxLatencyMs latency value for latency-based path computation strategies
     * @param maxLatencyTier2 latency tier 2 value for latency-based path computation strategies
     * @param maxPathCount find no more than this number of paths
     * @param includeProtectedPathAvailability calculate whether it is possible to create a protected path for the
     *      found paths
     * @return a PathDto containing the list of paths together with their parameters
     */
    CompletableFuture<PathsDto> getPaths(
            SwitchId srcSwitch, SwitchId dstSwitch, FlowEncapsulationType encapsulationType,
            PathComputationStrategy pathComputationStrategy, Duration maxLatencyMs, Duration maxLatencyTier2,
            Integer maxPathCount, Boolean includeProtectedPathAvailability);
}
