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

import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2;

import java.util.Optional;
import java.util.stream.Stream;

public final class BaseFlowEndpointV2Validator {

    // Private constructor to prevent instantiation of this class
    private BaseFlowEndpointV2Validator() {
        throw new AssertionError();
    }

    /**
     * Validate baseFlowEndpointV2.
     *
     * @param baseFlowEndpointV2 baseFlowEndpointV2
     */
    public static Stream<Optional<String>> validateBaseFlowEndpointV2(BaseFlowEndpointV2 baseFlowEndpointV2) {
        return Stream.of(validateBaseFlowEndpointV2SwitchId(baseFlowEndpointV2),
                validateBaseFlowEndpointV2PortNumber(baseFlowEndpointV2),
                validateBaseFlowEndpointV2VlanId(baseFlowEndpointV2),
                validateBaseFlowEndpointV2InnerVlanId(baseFlowEndpointV2));
    }

    private static Optional<String> validateBaseFlowEndpointV2SwitchId(BaseFlowEndpointV2 baseFlowEndpointV2) {
        //validate baseFlowEndpointV2 switchId is not null
        if (baseFlowEndpointV2.getSwitchId() == null) {
            return Optional.of("SwitchId is required");
        }
        return Optional.empty();
    }

    /**
     * Validate baseFlowEndpointV2 portNumber.
     *
     * @param baseFlowEndpointV2 baseFlowEndpointV2
     */
    private static Optional<String> validateBaseFlowEndpointV2PortNumber(BaseFlowEndpointV2 baseFlowEndpointV2) {
        //validate baseFlowEndpointV2 portNumber is not null
        if (baseFlowEndpointV2.getPortNumber() == null) {
            return Optional.of("PortNumber is required");
        }

        //validate baseFlowEndpointV2 portNumber is not be negative
        if (baseFlowEndpointV2.getPortNumber() < 0) {
            return Optional.of("PortNumber must be non-negative");
        }
        return Optional.empty();
    }

    /**
     * Validate baseFlowEndpointV2 vlanId.
     *
     * @param baseFlowEndpointV2 baseFlowEndpointV2
     */
    private static Optional<String> validateBaseFlowEndpointV2VlanId(BaseFlowEndpointV2 baseFlowEndpointV2) {
        //validate baseFlowEndpointV2 vlanId is not be negative
        if (baseFlowEndpointV2.getVlanId() < 0) {
            return Optional.of("VlanId must be non-negative");
        }

        //validate baseFlowEndpointV2 vlanId is not more than 4095
        if (baseFlowEndpointV2.getVlanId() > 4094) {
            return Optional.of("VlanId must be less than 4095");
        }
        return Optional.empty();
    }

    /**
     * Validate baseFlowEndpointV2 innerVlanId.
     *
     * @param baseFlowEndpointV2 baseFlowEndpointV2
     */
    private static Optional<String> validateBaseFlowEndpointV2InnerVlanId(BaseFlowEndpointV2 baseFlowEndpointV2) {
        //validate baseFlowEndpointV2 innerVlanId is not be negative
        if (baseFlowEndpointV2.getInnerVlanId() < 0) {
            return Optional.of("InnerVlanId must be non-negative");
        }

        //validate baseFlowEndpointV2 innerVlanId is not more than 4095
        if (baseFlowEndpointV2.getInnerVlanId() > 4094) {
            return Optional.of("InnerVlanId must be less than 4095");
        }
        return Optional.empty();
    }
}
