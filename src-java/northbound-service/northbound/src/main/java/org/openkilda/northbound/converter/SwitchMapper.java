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
import org.openkilda.messaging.model.SwitchLocation;
import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
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
import org.openkilda.northbound.dto.v1.switches.SwitchLocationDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Date;

@Mapper(componentModel = "spring", uses = {FlowMapper.class},
        imports = {Date.class, MacAddress.class, SwitchLocationDto.class, SwitchLocationDtoV2.class})
public interface SwitchMapper {

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address.hostAddress", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    @Mapping(target = "location", expression = "java(new SwitchLocationDto("
            + "data.getLatitude(), data.getLongitude(), data.getStreet(), data.getCity(), data.getCountry()))")
    SwitchDto toSwitchDto(Switch data);

    /**
     * Convert {@link SwitchStatus} to {@link String} representation.
     */
    default SwitchChangeType convertStatus(SwitchStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case ACTIVE:
                return SwitchChangeType.ACTIVATED;
            case INACTIVE:
                return SwitchChangeType.DEACTIVATED;
            case REMOVED:
                return SwitchChangeType.REMOVED;
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
    @Mapping(target = "server42MacAddress", expression = "java(entry.getServer42MacAddress() == null ? null "
            + ": entry.getServer42MacAddress().toString())")
    SwitchPropertiesDto map(org.openkilda.messaging.model.SwitchPropertiesDto entry);

    @Mapping(target = "supportedTransitEncapsulation",
            expression = "java(entry.getSupportedTransitEncapsulation() == null ? null : "
                    + "entry.getSupportedTransitEncapsulation().stream()"
                    + ".map(e-> org.openkilda.messaging.payload.flow.FlowEncapsulationType.valueOf(e.toUpperCase()))"
                    + ".collect(java.util.stream.Collectors.toSet()))")
    @Mapping(target = "server42MacAddress", expression = "java(entry.getServer42MacAddress() == null ? null "
            + ": new MacAddress(entry.getServer42MacAddress()))")
    org.openkilda.messaging.model.SwitchPropertiesDto map(SwitchPropertiesDto entry);

    @Mapping(source = "upEventsCount", target = "upCount")
    @Mapping(source = "downEventsCount", target = "downCount")
    @Mapping(target = "date", expression = "java(Date.from(response.getTime()))")
    PortHistoryResponse map(PortHistoryPayload response);

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address.hostAddress", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    @Mapping(target = "location", expression = "java(new SwitchLocationDtoV2("
            + "data.getLatitude(), data.getLongitude(), data.getStreet(), data.getCity(), data.getCountry()))")
    SwitchDtoV2 map(Switch data);

    SwitchPatch map(SwitchPatchDto data);

    SwitchLocation map(SwitchLocationDtoV2 data);
}
