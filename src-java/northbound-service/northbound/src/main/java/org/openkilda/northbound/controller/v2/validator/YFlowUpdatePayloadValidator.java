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

import org.openkilda.northbound.dto.utils.Constraints;
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.stream.Stream;

public final class YFlowUpdatePayloadValidator {

    private YFlowUpdatePayloadValidator() {
        throw new AssertionError();
    }

    /**
     * Validate yFlowUpdatePayload.
     *
     * @param flow yFlowUpdatePayload
     */
    public static Stream<Optional<String>> validateYFlowUpdatePayload(YFlowUpdatePayload flow) {
        return Stream.concat(Stream.concat(Stream.of(
                                validateFlowSharedEndpoint(flow),
                                validateFlowMaximumBandwidth(flow),
                                validateFlowPathComputationStrategy(flow),
                                validateFlowEncapsulationType(flow),
                                validateFlowMaxLatency(flow),
                                validateFlowMaxLatencyTier2(flow)),
                        validateFlowSubFlows(flow)),
                YFlowSharedEndpointValidator.validateYFlowSharedEndpoint(flow.getSharedEndpoint()));
    }

    private static Optional<String> validateFlowEncapsulationType(YFlowUpdatePayload flow) {
        if (StringUtils.isEmpty(flow.getEncapsulationType())) {
            return Optional.of(Constraints.BLANK_ENCAPSULATION_TYPE_MESSAGE);
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowPathComputationStrategy(YFlowUpdatePayload flow) {
        if (StringUtils.isEmpty(flow.getPathComputationStrategy())) {
            return Optional.of(Constraints.BLANK_PATH_COMPUTATION_STRATEGY_MESSAGE);
        }
        return Optional.empty();
    }

    /**
     * Validate flowSharedEndpoint.
     *
     * @param flow yFlowUpdatePayload
     */
    private static Optional<String> validateFlowSharedEndpoint(YFlowUpdatePayload flow) {
        if (flow.getSharedEndpoint() == null) {
            return Optional.of("SharedEndpoint is required");
        }
        return Optional.empty();
    }

    /**
     * Validate flowMaximumBandwidth.
     *
     * @param flow yFlowUpdatePayload
     */

    private static Optional<String> validateFlowMaximumBandwidth(YFlowUpdatePayload flow) {
        if (flow.getMaximumBandwidth() <= 0) {
            return Optional.of("MaximumBandwidth must be positive");
        }
        return Optional.empty();
    }


    /**
     * Validate flowMaxLatency.
     *
     * @param flow yFlowUpdatePayload
     */
    private static Optional<String> validateFlowMaxLatency(YFlowUpdatePayload flow) {
        if (flow.getMaxLatency() != null && flow.getMaxLatency() < 0) {
            return Optional.of("MaximumLatency must be non-negative");
        }
        return Optional.empty();
    }

    /**
     * Validate flowMaxLatencyTier2.
     *
     * @param flow yFlowUpdatePayload
     */
    private static Optional<String> validateFlowMaxLatencyTier2(YFlowUpdatePayload flow) {
        if (flow.getMaxLatencyTier2() != null && flow.getMaxLatencyTier2() < 0) {
            return Optional.of("MaximumLatencyTier2 must be non-negative");
        }
        return Optional.empty();
    }

    /**
     * Validate flowSubFlows.
     *
     * @param flow yFlowUpdatePayload
     */
    private static Stream<Optional<String>> validateFlowSubFlows(YFlowUpdatePayload flow) {
        Stream<Optional<String>> result = Stream.empty();
        if (CollectionUtils.isNotEmpty(flow.getSubFlows())) {
            for (SubFlowUpdatePayload subFlow : flow.getSubFlows()) {
                result = Stream.concat(result, SubFlowUpdatePayloadValidator.validateSubFlowUpdatePayload(subFlow));
            }
        }
        return result;
    }
}
