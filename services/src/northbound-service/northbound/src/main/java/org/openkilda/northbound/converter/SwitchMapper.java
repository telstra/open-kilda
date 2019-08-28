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

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.RulesValidationEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.northbound.dto.v1.switches.MeterInfoDto;
import org.openkilda.northbound.dto.v1.switches.MeterMisconfiguredInfoDto;
import org.openkilda.northbound.dto.v1.switches.MetersSyncDto;
import org.openkilda.northbound.dto.v1.switches.MetersValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Date;

@Mapper(componentModel = "spring", uses = {FlowMapper.class}, imports = {Date.class})
public interface SwitchMapper {

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address.hostAddress", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    SwitchDto toSwitchDto(Switch data);

    /**
     * Convert {@link SwitchStatus} to {@link String} representation.
     */
    default String convertStatus(SwitchStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case ACTIVE:
                return SwitchChangeType.ACTIVATED.name();
            case INACTIVE:
                return SwitchChangeType.DEACTIVATED.name();
            case REMOVED:
                return SwitchChangeType.REMOVED.name();
            default:
                throw new IllegalArgumentException("Unsupported Switch status: " + status);
        }
    }

    @Mapping(source = "rules.excess", target = "excessRules")
    @Mapping(source = "rules.missing", target = "missingRules")
    @Mapping(source = "rules.proper", target = "properRules")
    @Mapping(source = "rules.installed", target = "installedRules")
    RulesSyncResult toRulesSyncResult(SwitchSyncResponse response);

    SwitchSyncResult toSwitchSyncResult(SwitchSyncResponse response);

    RulesSyncDto toRulesSyncDto(RulesSyncEntry data);

    MetersSyncDto toMetersSyncDto(MetersSyncEntry data);

    SwitchValidationResult toSwitchValidationResult(SwitchValidationResponse response);

    @Mapping(source = "rules.excess", target = "excessRules")
    @Mapping(source = "rules.missing", target = "missingRules")
    @Mapping(source = "rules.proper", target = "properRules")
    RulesValidationResult toRulesValidationResult(SwitchValidationResponse response);

    RulesValidationDto toRulesValidationDto(RulesValidationEntry data);

    MetersValidationDto toMetersValidationDto(MetersValidationEntry data);

    MeterInfoDto toMeterInfoDto(MeterInfoEntry data);

    MeterMisconfiguredInfoDto toMeterMisconfiguredInfoDto(MeterMisconfiguredInfoEntry data);

    @Mapping(target = "supportedTransitEncapsulation",
            expression = "java(entry.getSupportedTransitEncapsulation().stream()"
                       + ".map(e -> e.toString().toLowerCase()).collect(java.util.stream.Collectors.toList()))")
    SwitchPropertiesDto map(org.openkilda.messaging.model.SwitchPropertiesDto entry);

    @Mapping(target = "supportedTransitEncapsulation",
            expression = "java(entry.getSupportedTransitEncapsulation().stream()"
                    + ".map(e-> org.openkilda.messaging.payload.flow.FlowEncapsulationType.valueOf(e.toUpperCase()))"
                    + ".collect(java.util.stream.Collectors.toSet()))")
    org.openkilda.messaging.model.SwitchPropertiesDto map(SwitchPropertiesDto entry);

    @Mapping(target = "date", expression = "java(Date.from(response.getTime()))")
    PortHistoryResponse map(PortHistoryPayload response);

    default String toSwithId(SwitchId switchId) {
        return switchId.toString();
    }
}
