/* Copyright 2022 Telstra Open Source
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

package org.openkilda.northbound.controller;

import org.openkilda.messaging.Utils;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;

import java.util.Optional;
import java.util.stream.Stream;

public abstract class BaseFlowController extends BaseController {
    protected void verifyRequest(FlowRequestV2 request) {
        exposeBodyValidationResults(Stream.concat(
                verifyFlowEndpoint(request.getSource(), "source"),
                verifyFlowEndpoint(request.getDestination(), "destination")));
    }

    protected Stream<Optional<String>> verifyFlowEndpoint(FlowEndpointV2 endpoint, String name) {
        return Stream.of(
                verifyEndpointVlanId(name, "vlanId", endpoint.getVlanId()),
                verifyEndpointVlanId(name, "innerVlanId", endpoint.getInnerVlanId()));
    }

    protected Optional<String> verifyEndpointVlanId(String endpoint, String field, int value) {
        if (!Utils.validateVlanRange(value)) {
            return Optional.of(String.format("Invalid %s value %d into %s endpoint", field, value, endpoint));
        }
        return Optional.empty();
    }
}

