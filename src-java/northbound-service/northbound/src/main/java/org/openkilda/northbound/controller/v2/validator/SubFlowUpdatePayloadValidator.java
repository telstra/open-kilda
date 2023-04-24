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

import static org.openkilda.northbound.validator.FlowEndpointV2Validator.validateFlowEndpointV2;

import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload;

import java.util.Optional;
import java.util.stream.Stream;

public final class SubFlowUpdatePayloadValidator {

    private SubFlowUpdatePayloadValidator() {
        throw new AssertionError();
    }

    /**
     * Validate subFlowUpdatePayload.
     *
     * @param subFlowUpdatePayload subFlowUpdatePayload
     */
    public static Stream<Optional<String>> validateSubFlowUpdatePayload(SubFlowUpdatePayload subFlowUpdatePayload) {
        return Stream.concat(Stream.concat(Stream.of(
                                validateSubFlowEndpoint(subFlowUpdatePayload)),
                        validateFlowEndpointV2(subFlowUpdatePayload.getEndpoint())),
                YFlowSharedEndpointEncapsulationValidator.validateBaseFlowEndpointV2(
                        subFlowUpdatePayload.getSharedEndpoint()));
    }

    private static Optional<String> validateSubFlowEndpoint(SubFlowUpdatePayload subFlowUpdatePayload) {
        //validate subflow endpoint is not null
        if (subFlowUpdatePayload.getEndpoint() == null) {
            return Optional.of("Subflow endpoint is required");
        }
        return Optional.empty();
    }
}
