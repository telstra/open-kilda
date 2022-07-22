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

import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;

import java.util.Optional;
import java.util.stream.Stream;

public final class FlowEndpointV2Validator {
    private FlowEndpointV2Validator() {
        throw new AssertionError();
    }

    /**
     * Validate flowEndpointV2.
     *
     * @param flowEndpointV2 flowEndpointV2
     */
    public static Stream<Optional<String>> validateFlowEndpointV2(FlowEndpointV2 flowEndpointV2) {
        return Stream.concat(Stream.of(
                        validateFlowEndpointV2DetectConnectedDevices(flowEndpointV2)),
                BaseFlowEndpointV2Validator.validateBaseFlowEndpointV2(flowEndpointV2));
    }

    /**
     * Validate flowEndpointV2.
     *
     * @param flowEndpointV2 flowEndpointV2
     */
    public static Optional<String> validateFlowEndpointV2DetectConnectedDevices(FlowEndpointV2 flowEndpointV2) {
        //validate detectedConnectedDevices is not null
        if (flowEndpointV2.getDetectConnectedDevices() == null) {
            return Optional.of("DetectConnectedDevices is required");
        }
        return Optional.empty();
    }
}

