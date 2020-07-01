/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.mappers;

import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesValidationEntry;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class ValidationMapper {
    public static final ValidationMapper INSTANCE = Mappers.getMapper(ValidationMapper.class);

    /**
     * Produce {@code SwitchValidationResponse} from {@code SwitchValidationContext}.
     */
    public SwitchValidationResponse toSwitchResponse(SwitchValidationContext validationContext) {
        if (validationContext == null) {
            return null;
        }

        SwitchValidationResponse.SwitchValidationResponseBuilder response = SwitchValidationResponse.builder();
        if (validationContext.getOfFlowsValidationReport() != null) {
            response.rules(mapReport(validationContext.getOfFlowsValidationReport()));
        }
        if (validationContext.getMetersValidationReport() != null) {
            response.meters(mapReport(validationContext.getMetersValidationReport()));
        }

        return response.build();
    }

    @Mapping(source = "missingRules", target = "missing")
    @Mapping(source = "properRules", target = "proper")
    @Mapping(source = "excessRules", target = "excess")
    @Mapping(source = "misconfiguredRules", target = "misconfigured")
    public abstract RulesValidationEntry mapReport(ValidateRulesResult report);

    @Mapping(source = "missingMeters", target = "missing")
    @Mapping(source = "misconfiguredMeters", target = "misconfigured")
    @Mapping(source = "properMeters", target = "proper")
    @Mapping(source = "excessMeters", target = "excess")
    public abstract MetersValidationEntry mapReport(ValidateMetersResult report);
}
