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

import org.openkilda.messaging.payload.network.PathDto;
import org.openkilda.messaging.payload.network.PathsDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.PathValidateResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service to handle network requests.
 */
public interface NetworkService {

    /**
     * Gets paths between two switches.
     */
    CompletableFuture<PathsDto> getPaths(
            SwitchId srcSwitch, SwitchId dstSwitch, FlowEncapsulationType encapsulationType,
            PathComputationStrategy pathComputationStrategy, Duration maxLatencyMs, Duration maxLatencyTier2,
            Integer maxPathCount);

    /**
     * Validates that a flow with the given path can possibly be created. If it is not possible,
     * it responds with the reasons, such as: not enough bandwidth, requested latency it too low, there is no
     * links between the selected switches, and so on.
     * @param path a path provided by a user
     * @return either a successful response or the list of errors
     */
    CompletableFuture<PathValidateResponse> validateFlowPath(PathDto path);
}
