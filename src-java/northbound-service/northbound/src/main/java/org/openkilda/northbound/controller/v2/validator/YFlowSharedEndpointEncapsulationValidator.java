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

import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation;

import java.util.Optional;
import java.util.stream.Stream;

public final class YFlowSharedEndpointEncapsulationValidator {

    // Private constructor to prevent instantiation of this class
    private YFlowSharedEndpointEncapsulationValidator() {
        throw new AssertionError();
    }

    /**
     * Validate baseFlowEndpointV2.
     *
     * @param flowSharedEndpointEncapsulation flowSharedEndpointEncapsulation
     */
    public static Stream<Optional<String>> validateBaseFlowEndpointV2(
            YFlowSharedEndpointEncapsulation flowSharedEndpointEncapsulation) {
        return Stream.of(
                validateBaseFlowEndpointV2VlanId(flowSharedEndpointEncapsulation),
                validateBaseFlowEndpointV2InnerVlanId(flowSharedEndpointEncapsulation));
    }

    private static Optional<String> validateBaseFlowEndpointV2VlanId(
            YFlowSharedEndpointEncapsulation flowSharedEndpointEncapsulation) {
        //validate flowSharedEndpointEncapsulation vlanId is not be negative
        if (flowSharedEndpointEncapsulation.getVlanId() < 0) {
            return Optional.of("VlanId must be non-negative");
        }

        //validate flowSharedEndpointEncapsulation vlanId is not more than 4095
        if (flowSharedEndpointEncapsulation.getVlanId() > 4094) {
            return Optional.of("VlanId must be less than 4095");
        }
        return Optional.empty();
    }

    private static Optional<String> validateBaseFlowEndpointV2InnerVlanId(
            YFlowSharedEndpointEncapsulation flowSharedEndpointEncapsulation) {
        //validate flowSharedEndpointEncapsulation innerVlanId is not be negative
        if (flowSharedEndpointEncapsulation.getInnerVlanId() < 0) {
            return Optional.of("InnerVlanId must be non-negative");
        }

        //validate flowSharedEndpointEncapsulation innerVlanId is not more than 4095
        if (flowSharedEndpointEncapsulation.getInnerVlanId() > 4094) {
            return Optional.of("InnerVlanId must be less than 4095");
        }
        return Optional.empty();
    }
}

