/* Copyright 2018 Telstra Open Source
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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.command.system.FeatureToggleRequest;
import org.openkilda.messaging.info.system.FeatureTogglesResponse;
import org.openkilda.messaging.payload.FeatureTogglePayload;

import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface FeatureTogglesMapper {

    /**
     * Converts {@code}FeatureTogglesResponse{@code} to {@code}FeatureTogglePayload{@code}.
     *
     * @param response the {@code}FeatureTogglesResponse{@code} object.
     * @return appropriate {@code}FeatureTogglePayload{@code}.
     */
    default FeatureTogglePayload toDto(FeatureTogglesResponse response) {
        return new FeatureTogglePayload(response.getSyncRulesEnabled(), response.getReflowOnSwitchActivationEnabled(),
                response.getCreateFlowEnabled(), response.getUpdateFlowEnabled(), response.getDeleteFlowEnabled(),
                response.getPushFlowEnabled(), response.getUnpushFlowEnabled());
    }

    // todo: replace with new correlation_id feature
    default FeatureToggleRequest toRequest(FeatureTogglePayload request) {
        return toRequest(request, UUID.randomUUID().toString());
    }

    /**
     * Converts {@code}FeatureTogglePayload{@code} to {@code}FeatureToggleRequest{@code}.
     *
     * @param request the {@code}FeatureTogglePayload{@code} object.
     * @param correlationId the correlation ID.
     * @return appropriate {@code}FeatureToggleRequest{@code}.
     */
    default FeatureToggleRequest toRequest(FeatureTogglePayload request, String correlationId) {
        return new FeatureToggleRequest(request.getSyncRulesEnabled(), request.getReflowOnSwitchActivationEnabled(),
                request.getCreateFlowEnabled(), request.getUpdateFlowEnabled(), request.getDeleteFlowEnabled(),
                request.getPushFlowEnabled(), request.getUnpushFlowEnabled(), correlationId);
    }

}
