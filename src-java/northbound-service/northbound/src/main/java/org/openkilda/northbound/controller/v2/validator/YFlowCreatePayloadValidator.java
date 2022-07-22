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

import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Optional;
import java.util.stream.Stream;

public final class YFlowCreatePayloadValidator {
    private YFlowCreatePayloadValidator() {
        throw new AssertionError();
    }

    /**
     * Validate yFlowCreatePayload.
     *
     * @param flow yFlowCreatePayload
     */
    public static Stream<Optional<String>> validateYFlowCreatePayload(YFlowCreatePayload flow) {
        return Stream.concat(Stream.concat(Stream.of(
                                validateFlowSharedEndpoint(flow),
                                validateFlowMaximumBandwidth(flow),
                                validateFlowMaxLatency(flow),
                                validateFlowMaxLatencyTier2(flow)),
                        validateFlowSubFlows(flow)),
                YFlowSharedEndpointValidator.validateYFlowSharedEndpoint(flow.getSharedEndpoint()));
    }

    private static Stream<Optional<String>> validateFlowSubFlows(YFlowCreatePayload flow) {
        Stream<Optional<String>> result = Stream.empty();
        if (CollectionUtils.isNotEmpty(flow.getSubFlows())) {
            for (SubFlowUpdatePayload subFlow : flow.getSubFlows()) {
                result = Stream.concat(result, SubFlowUpdatePayloadValidator.validateSubFlowUpdatePayload(subFlow));
            }
        }
        return result;
    }

    private static Optional<String> validateFlowMaxLatencyTier2(YFlowCreatePayload flow) {
        if (flow.getMaxLatencyTier2() != null && flow.getMaxLatencyTier2() < 0) {
            return Optional.of("MaximumLatencyTier2 must be non-negative");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowMaxLatency(YFlowCreatePayload flow) {
        if (flow.getMaxLatency() != null && flow.getMaxLatency() < 0) {
            return Optional.of("MaximumLatency must be non-negative");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowMaximumBandwidth(YFlowCreatePayload flow) {
        if (flow.getMaximumBandwidth() <= 0) {
            return Optional.of("MaximumBandwidth must be positive");
        }
        return Optional.empty();
    }

    private static Optional<String> validateFlowSharedEndpoint(YFlowCreatePayload flow) {
        if (flow.getSharedEndpoint() == null) {
            return Optional.of("SharedEndpoint is required");
        }
        return Optional.empty();
    }
}
