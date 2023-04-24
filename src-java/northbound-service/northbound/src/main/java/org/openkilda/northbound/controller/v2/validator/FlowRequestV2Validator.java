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

import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.stream.Stream;

public final class FlowRequestV2Validator {

    private FlowRequestV2Validator() {
        throw new AssertionError();
    }

    /**
     * Validate flowRequestV2.
     *
     * @param flowRequestV2 flowRequestV2
     */
    public static Stream<Optional<String>> validateFlowRequestV2(FlowRequestV2 flowRequestV2) {
        return Stream.concat(Stream.concat(Stream.of(
                                validateFlowRequestV2FlowId(flowRequestV2),
                                validateFlowRequestV2Source(flowRequestV2),
                                validateFlowRequestV2Destination(flowRequestV2),
                                validateFlowRequestV2MaximumBandwidth(flowRequestV2),
                                validateFlowRequestV2MaxLatency(flowRequestV2),
                                validateFlowRequestV2MaxLatencyTier2(flowRequestV2)),
                        FlowEndpointV2Validator.validateFlowEndpointV2(flowRequestV2.getSource())),
                FlowEndpointV2Validator.validateFlowEndpointV2(flowRequestV2.getDestination()));
    }

    /**
     * Validate flowRequestV2.
     *
     * @param flowRequestV2 flowRequestV2
     */
    private static Optional<String> validateFlowRequestV2FlowId(FlowRequestV2 flowRequestV2) {
        //validate baseFlowEndpointV2 switchId is not null
        if (StringUtils.isEmpty(flowRequestV2.getFlowId())) {
            return Optional.of("FlowId is required");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowRequestV2Source(FlowRequestV2 flowRequestV2) {
        //validate source is not null
        if (flowRequestV2.getSource() == null) {
            return Optional.of("Source is required");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowRequestV2Destination(FlowRequestV2 flowRequestV2) {
        //validate destination is not null
        if (flowRequestV2.getDestination() == null) {
            return Optional.of("Destination is required");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowRequestV2MaximumBandwidth(FlowRequestV2 flowRequestV2) {
        //validate MaximumBandwidth is non-negative
        if (flowRequestV2.getMaximumBandwidth() < 0) {
            return Optional.of("MaximumBandwidth must be non-negative");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowRequestV2MaxLatency(FlowRequestV2 flowRequestV2) {
        //validate MaxLatency is non-negative
        if (flowRequestV2.getMaxLatency() < 0) {
            return Optional.of("MaxLatency must be non-negative");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowRequestV2MaxLatencyTier2(FlowRequestV2 flowRequestV2) {
        //validate MaxLatencyTier2 is non-negative
        if (flowRequestV2.getMaxLatencyTier2() < 0) {
            return Optional.of("MaxLatencyTier2 must be non-negative");
        }
        return Optional.empty();
    }
}

