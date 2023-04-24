/* Copyright 2023 Telstra Open Source
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

package org.openkilda.northbound.validator;

import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint;

import java.util.Optional;
import java.util.stream.Stream;

public final class YFlowSharedEndpointValidator {
    private YFlowSharedEndpointValidator() {
        throw new AssertionError();
    }

    /**
     * Validate yFlowSharedEndpoint.
     *
     * @param sharedEndpoint yFlowSharedEndpoint
     */
    public static Stream<Optional<String>> validateYFlowSharedEndpoint(YFlowSharedEndpoint sharedEndpoint) {
        return Stream.of(
                validateYFlowSharedEndpointSwitchId(sharedEndpoint),
                validateYFlowSharedEndpointPortNumber(sharedEndpoint));
    }

    /**
     * Validate yFlowSharedEndpoint switchId.
     *
     * @param sharedEndpoint yFlowSharedEndpoint
     */
    public static Optional<String> validateYFlowSharedEndpointSwitchId(YFlowSharedEndpoint sharedEndpoint) {
        //validate sharedEndpoint switchId is not null
        if (sharedEndpoint.getSwitchId() == null) {
            return Optional.of("SwitchId is required");
        }
        return Optional.empty();
    }

    /**
     * Validate yFlowSharedEndpoint portNumber.
     *
     * @param sharedEndpoint yFlowSharedEndpoint
     */
    public static Optional<String> validateYFlowSharedEndpointPortNumber(YFlowSharedEndpoint sharedEndpoint) {
        //validate sharedEndpoint portNumber is not be negative
        if (sharedEndpoint.getPortNumber() < 0) {
            return Optional.of("PortNumber must be non-negative");
        }
        return Optional.empty();
    }
}

