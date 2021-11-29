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
import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.messaging.info.switches.GroupInfoEntry.BucketEntry;
import org.openkilda.messaging.info.switches.GroupSyncEntry;
import org.openkilda.messaging.info.switches.GroupsValidationEntry;
import org.openkilda.messaging.info.switches.LogicalPortsSyncEntry;
import org.openkilda.messaging.info.switches.LogicalPortsValidationEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.MetersValidationEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.RulesValidationEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.messaging.model.SwitchAvailabilityData;
import org.openkilda.messaging.model.SwitchAvailabilityEntry;
import org.openkilda.messaging.model.SwitchLocation;
import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchStatus;
import org.openkilda.northbound.dto.v1.switches.GroupInfoDto;
import org.openkilda.northbound.dto.v1.switches.GroupInfoDto.BucketDto;
import org.openkilda.northbound.dto.v1.switches.GroupsSyncDto;
import org.openkilda.northbound.dto.v1.switches.GroupsValidationDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortsSyncDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortsValidationDto;
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
import org.openkilda.northbound.dto.v2.switches.SwitchConnectEntry;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring",
        uses = {FlowMapper.class, InstantMapper.class},
        imports = {Date.class, MacAddress.class, SwitchLocationDto.class, SwitchLocationDtoV2.class})
public abstract class SwitchMapper {

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    @Mapping(target = "location", expression = "java(new SwitchLocationDto("
            + "data.getLatitude(), data.getLongitude(), data.getStreet(), data.getCity(), data.getCountry()))")
    public abstract SwitchDto toSwitchDto(Switch data);

    /**
     * Convert {@link SwitchStatus} to {@link String} representation.
     */
    public SwitchChangeType convertStatus(SwitchStatus status) {
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
    @Mapping(target = "missingRulesHex", ignore = true)
    @Mapping(target = "properRulesHex", ignore = true)
    @Mapping(target = "excessHex", ignore = true)
    @Mapping(target = "installedHex", ignore = true)
    public abstract RulesSyncResult toRulesSyncResult(SwitchSyncResponse response);

    public abstract SwitchSyncResult toSwitchSyncResult(SwitchSyncResponse response);

    @Mapping(target = "missingHex", ignore = true)
    @Mapping(target = "misconfiguredHex", ignore = true)
    @Mapping(target = "properHex", ignore = true)
    @Mapping(target = "excessHex", ignore = true)
    @Mapping(target = "installedHex", ignore = true)
    @Mapping(target = "removedHex", ignore = true)
    public abstract RulesSyncDto toRulesSyncDto(RulesSyncEntry data);

    public abstract MetersSyncDto toMetersSyncDto(MetersSyncEntry data);

    public abstract GroupsSyncDto toGroupsSyncDto(GroupSyncEntry data);

    public abstract LogicalPortsSyncDto toLogicalPortsSyncDto(LogicalPortsSyncEntry data);

    @Mapping(target = "rules.missingHex", ignore = true)
    @Mapping(target = "rules.misconfiguredHex", ignore = true)
    @Mapping(target = "rules.properHex", ignore = true)
    @Mapping(target = "rules.excessHex", ignore = true)
    public abstract SwitchValidationResult toSwitchValidationResult(SwitchValidationResponse response);

    @Mapping(source = "rules.excess", target = "excessRules")
    @Mapping(source = "rules.missing", target = "missingRules")
    @Mapping(source = "rules.proper", target = "properRules")
    @Mapping(target = "missingRulesHex", ignore = true)
    @Mapping(target = "properRulesHex", ignore = true)
    @Mapping(target = "excessHex", ignore = true)
    public abstract RulesValidationResult toRulesValidationResult(SwitchValidationResponse response);

    @Mapping(target = "missingHex", ignore = true)
    @Mapping(target = "misconfiguredHex", ignore = true)
    @Mapping(target = "properHex", ignore = true)
    @Mapping(target = "excessHex", ignore = true)
    public abstract RulesValidationDto toRulesValidationDto(RulesValidationEntry data);

    public abstract MetersValidationDto toMetersValidationDto(MetersValidationEntry data);

    public abstract LogicalPortsValidationDto toLogicalPortsValidationDto(LogicalPortsValidationEntry data);

    public abstract MeterInfoDto toMeterInfoDto(MeterInfoEntry data);

    public abstract GroupsValidationDto toMetersGroupsValidationDto(GroupsValidationEntry data);

    public abstract GroupInfoDto toGroupInfoDto(GroupInfoEntry data);

    public abstract BucketDto toBucketDto(BucketEntry data);

    public abstract MeterMisconfiguredInfoDto toMeterMisconfiguredInfoDto(MeterMisconfiguredInfoEntry data);

    @Mapping(target = "supportedTransitEncapsulation",
            expression = "java(entry.getSupportedTransitEncapsulation().stream()"
                       + ".map(e -> e.toString().toLowerCase()).collect(java.util.stream.Collectors.toList()))")
    @Mapping(target = "server42MacAddress", expression = "java(entry.getServer42MacAddress() == null ? null "
            + ": entry.getServer42MacAddress().toString())")
    public abstract SwitchPropertiesDto map(org.openkilda.messaging.model.SwitchPropertiesDto entry);

    @Mapping(target = "supportedTransitEncapsulation",
            expression = "java(entry.getSupportedTransitEncapsulation() == null ? null : "
                    + "entry.getSupportedTransitEncapsulation().stream()"
                    + ".map(e-> org.openkilda.messaging.payload.flow.FlowEncapsulationType.valueOf(e.toUpperCase()))"
                    + ".collect(java.util.stream.Collectors.toSet()))")
    @Mapping(target = "server42MacAddress", expression = "java(entry.getServer42MacAddress() == null ? null "
            + ": new MacAddress(entry.getServer42MacAddress()))")
    public abstract org.openkilda.messaging.model.SwitchPropertiesDto map(SwitchPropertiesDto entry);

    @Mapping(source = "upEventsCount", target = "upCount")
    @Mapping(source = "downEventsCount", target = "downCount")
    @Mapping(target = "date", expression = "java(Date.from(response.getTime()))")
    public abstract PortHistoryResponse map(PortHistoryPayload response);

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    @Mapping(target = "location", expression = "java(new SwitchLocationDtoV2("
            + "data.getLatitude(), data.getLongitude(), data.getStreet(), data.getCity(), data.getCountry()))")
    public abstract SwitchDtoV2 map(Switch data);

    public abstract SwitchPatch map(SwitchPatchDto data);

    public abstract SwitchLocation map(SwitchLocationDtoV2 data);

    @Mapping(target = "state", source = "status")
    public abstract SwitchConnectionsResponse map(
            org.openkilda.messaging.nbtopology.response.SwitchConnectionsResponse data);

    /**
     * Convert {@link SwitchAvailabilityData} into list of {@link SwitchConnectEntry} elements.
     */
    public List<SwitchConnectEntry> map(SwitchAvailabilityData value) {
        return value.getConnections().stream()
                .map(this::map)
                .collect(Collectors.toList());
    }

    public abstract SwitchConnectEntry map(SwitchAvailabilityEntry data);

    /**
     * Produce string representation of {@link IpSocketAddress}.
     */
    public String map(IpSocketAddress address) {
        if (address == null) {
            return null;
        }
        return String.format("%s:%d", address.getAddress(), address.getPort());
    }
}
