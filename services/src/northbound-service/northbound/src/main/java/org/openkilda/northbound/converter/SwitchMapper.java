/* Copyright 2019 Telstra Open Source
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

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesValidationEntry;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.switches.MeterInfoDto;
import org.openkilda.northbound.dto.v1.switches.MeterMisconfiguredInfoDto;
import org.openkilda.northbound.dto.v1.switches.MetersValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SwitchMapper {

    /**
     * Convert {@link SwitchInfoData} to {@link SwitchDto}.
     */
    default SwitchDto toSwitchDto(SwitchInfoData data) {
        if (data == null) {
            return null;
        }
        SwitchDto dto = SwitchDto.builder()
                .switchId(data.getSwitchId().toString())
                .address(data.getAddress())
                .hostname(data.getHostname())
                .description(data.getDescription())
                .state(data.getState().toString())
                .underMaintenance(data.isUnderMaintenance())
                .build();

        if (data.getSwitchView() != null) {
            dto.setOfVersion(data.getSwitchView().getOfVersion());
            if (data.getSwitchView().getDescription() != null) {
                dto.setManufacturer(data.getSwitchView().getDescription().getManufacturer());
                dto.setHardware(data.getSwitchView().getDescription().getHardware());
                dto.setSoftware(data.getSwitchView().getDescription().getSoftware());
                dto.setSerialNumber(data.getSwitchView().getDescription().getSerialNumber());
            }
        }

        return dto;
    }

    RulesSyncResult toRulesSyncResult(SyncRulesResponse response);

    SwitchValidationResult toSwitchValidationResult(SwitchValidationResponse response);

    @Mapping(source = "rules.excess", target = "excessRules")
    @Mapping(source = "rules.missing", target = "missingRules")
    @Mapping(source = "rules.proper", target = "properRules")
    RulesValidationResult toRulesValidationResult(SwitchValidationResponse response);

    RulesValidationDto toRulesValidationResult(RulesValidationEntry data);

    MetersValidationDto toMetersValidationResult(MetersValidationEntry data);

    MeterInfoDto toMeterInfoDto(MeterInfoEntry data);

    MeterMisconfiguredInfoDto toMeterMisconfiguredInfoDto(MeterMisconfiguredInfoEntry data);

    default String toSwithId(SwitchId switchId) {
        return switchId.toString();
    }
}
