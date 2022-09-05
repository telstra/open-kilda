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
import org.openkilda.messaging.info.switches.LogicalPortType;
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
import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.GroupsValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.LogicalPortInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.LogicalPortsValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.MeterInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MetersValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.RulesValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.SwitchValidationResponseV2;
import org.openkilda.messaging.info.switches.v2.action.BaseAction;
import org.openkilda.messaging.info.switches.v2.action.CopyFieldActionEntry;
import org.openkilda.messaging.info.switches.v2.action.GroupActionEntry;
import org.openkilda.messaging.info.switches.v2.action.MeterActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PopVlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PopVxlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PortOutActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PushVlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PushVxlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.SetFieldActionEntry;
import org.openkilda.messaging.info.switches.v2.action.SwapFieldActionEntry;
import org.openkilda.messaging.model.ExcludeFilter;
import org.openkilda.messaging.model.IncludeFilter;
import org.openkilda.messaging.model.SwitchAvailabilityData;
import org.openkilda.messaging.model.SwitchAvailabilityEntry;
import org.openkilda.messaging.model.SwitchLocation;
import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchStatus;
import org.openkilda.northbound.dto.v1.switches.GroupInfoDto;
import org.openkilda.northbound.dto.v1.switches.GroupInfoDto.BucketDto;
import org.openkilda.northbound.dto.v1.switches.GroupsSyncDto;
import org.openkilda.northbound.dto.v1.switches.GroupsValidationDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortInfoDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortMisconfiguredInfoDto;
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
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v2.action.CopyFieldActionDto;
import org.openkilda.northbound.dto.v2.action.GroupActionDto;
import org.openkilda.northbound.dto.v2.action.MeterActionDto;
import org.openkilda.northbound.dto.v2.action.PopVlanActionDto;
import org.openkilda.northbound.dto.v2.action.PopVxlanActionDto;
import org.openkilda.northbound.dto.v2.action.PortOutActionDto;
import org.openkilda.northbound.dto.v2.action.PushVlanActionDto;
import org.openkilda.northbound.dto.v2.action.PushVxlanActionDto;
import org.openkilda.northbound.dto.v2.action.SetFieldActionDto;
import org.openkilda.northbound.dto.v2.action.SwapFieldActionDto;
import org.openkilda.northbound.dto.v2.switches.GroupInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.GroupsValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.LogicalPortInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.LogicalPortsValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.MeterInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.MetersValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.RuleInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.RulesValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectEntry;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;
import org.openkilda.northbound.dto.v2.switches.SwitchValidationResultV2;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {
        FlowMapper.class, KildaTypeMapper.class, TimeMapper.class, FlowEncapsulationTypeMapper.class})
public abstract class SwitchMapper {

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    @Mapping(target = "location", source = "input")
    public abstract SwitchDto toSwitchDto(Switch input);

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

    /**
     * Convert SwitchValidationResponse V2 into api v1 rules representation.
     *
     * @param response switch validation api v2 response.
     * @return rules v1 api representation
     */
    @Mapping(target = "missingRulesHex", ignore = true)
    @Mapping(target = "properRulesHex", ignore = true)
    @Mapping(target = "excessHex", ignore = true)
    public RulesValidationResult toRulesValidationResult(SwitchValidationResponseV2 response) {
        if (response == null) {
            return null;
        }
        RulesValidationResult rulesValidationResult = new RulesValidationResult();
        rulesValidationResult.setExcessRules(response.getRules().getExcess().stream()
                .map(RuleInfoEntryV2::getCookie).collect(Collectors.toList()));
        rulesValidationResult.setProperRules(response.getRules().getProper().stream()
                .map(RuleInfoEntryV2::getCookie).collect(Collectors.toList()));
        rulesValidationResult.setMissingRules(response.getRules().getMissing().stream()
                .map(RuleInfoEntryV2::getCookie).collect(Collectors.toList()));

        return rulesValidationResult;
    }

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

    public abstract SwitchPropertiesDto map(org.openkilda.messaging.model.SwitchPropertiesDto entry);

    public abstract org.openkilda.messaging.model.SwitchPropertiesDto map(SwitchPropertiesDto entry);

    @Mapping(source = "upEventsCount", target = "upCount")
    @Mapping(source = "downEventsCount", target = "downCount")
    @Mapping(source = "time", target = "date")
    public abstract PortHistoryResponse map(PortHistoryPayload response);

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    @Mapping(target = "location", source = "input")
    public abstract SwitchDtoV2 map(Switch input);

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

    //from v2 to v1 api
    public abstract SwitchValidationResult toSwitchValidationResultV1(SwitchValidationResponseV2 response);

    public abstract BucketDto toBucketDtoV1(GroupInfoEntryV2.BucketEntry data);

    @Mapping(source = "buckets", target = "groupBuckets")
    @Mapping(target = "missingGroupBuckets", ignore = true)
    @Mapping(target = "excessGroupBuckets", ignore = true)
    public abstract GroupInfoDto toGroupInfoDtoV1(GroupInfoEntryV2 data);

    GroupInfoDto toGroupInfoDtoV1(MisconfiguredInfo<GroupInfoEntryV2> data) {
        if (data == null) {
            return null;
        }
        GroupInfoDto groupInfoDto = new GroupInfoDto();
        List<BucketDto> missingBuckets = new ArrayList<>();
        List<BucketDto> excessBuckets = new ArrayList<>();

        groupInfoDto.setGroupId(data.getExpected().getGroupId());

        for (GroupInfoEntryV2.BucketEntry bucketEntry : data.getDiscrepancies().getBuckets()) {
            if (data.getExpected().getBuckets().contains(bucketEntry)) {
                missingBuckets.add(toBucketDtoV1(bucketEntry));
                continue;
            }
            excessBuckets.add(toBucketDtoV1(bucketEntry));
        }
        groupInfoDto.setExcessGroupBuckets(excessBuckets);
        groupInfoDto.setMissingGroupBuckets(missingBuckets);

        return groupInfoDto;
    }

    @Mapping(target = "actual", ignore = true)
    @Mapping(target = "expected", ignore = true)
    public abstract LogicalPortInfoDto toLogicalPortInfoDtoV1(LogicalPortInfoEntryV2 data);

    LogicalPortInfoDto toLogicalPortInfoDtoV1(MisconfiguredInfo<LogicalPortInfoEntryV2> data) {
        if (data == null) {
            return null;
        }
        LogicalPortInfoEntryV2 expectedEntry = data.getExpected();
        LogicalPortInfoEntryV2 actualEntry = data.getDiscrepancies();

        LogicalPortInfoDto logicalPortInfoDto = new LogicalPortInfoDto();
        logicalPortInfoDto.setLogicalPortNumber(expectedEntry.getLogicalPortNumber());

        LogicalPortMisconfiguredInfoDto actual = new LogicalPortMisconfiguredInfoDto();
        LogicalPortMisconfiguredInfoDto expected = new LogicalPortMisconfiguredInfoDto();

        if (actualEntry != null) {
            if (actualEntry.getType() != null) {
                actual.setType(actualEntry.getType().getType());
                expected.setType(expectedEntry.getType().getType());
            }
            if (actualEntry.getPhysicalPorts() != null) {
                actual.setPhysicalPorts(actualEntry.getPhysicalPorts());
                expected.setPhysicalPorts(expectedEntry.getPhysicalPorts());
            }
        }
        logicalPortInfoDto.setType(Optional.ofNullable(actualEntry)
                .map(LogicalPortInfoEntryV2::getType)
                .map(LogicalPortType::getType)
                .orElse(expectedEntry.getType().getType()));
        logicalPortInfoDto.setPhysicalPorts(Optional.ofNullable(actualEntry)
                .map(LogicalPortInfoEntryV2::getPhysicalPorts)
                .orElse(expectedEntry.getPhysicalPorts()));
        logicalPortInfoDto.setActual(actual);
        logicalPortInfoDto.setExpected(expected);

        return logicalPortInfoDto;
    }

    @Mapping(target = "actual", ignore = true)
    @Mapping(target = "expected", ignore = true)
    public abstract MeterInfoDto toMeterInfoDtoV1(MeterInfoEntryV2 data);

    MeterInfoDto toMeterInfoDtoV1(MisconfiguredInfo<MeterInfoEntryV2> data) {
        if (data == null) {
            return null;
        }
        MeterInfoEntryV2 expectedEntity = data.getExpected();

        MeterInfoDto meterInfoDto = new MeterInfoDto();

        meterInfoDto.setMeterId(expectedEntity.getMeterId());
        meterInfoDto.setFlowId(expectedEntity.getFlowId());

        MeterMisconfiguredInfoDto actual = new MeterMisconfiguredInfoDto();
        MeterMisconfiguredInfoDto expected = new MeterMisconfiguredInfoDto();

        MeterInfoEntryV2 actualEntity = data.getDiscrepancies();

        if (actualEntity != null) {
            if (actualEntity.getBurstSize() != null) {
                actual.setBurstSize(actualEntity.getBurstSize());
                expected.setBurstSize(expectedEntity.getBurstSize());
            }
            if (actualEntity.getFlags() != null) {
                actual.setFlags(actualEntity.getFlags().toArray(new String[0]));
                expected.setFlags(expectedEntity.getFlags().toArray(new String[0]));
            }
            if (actualEntity.getRate() != null) {
                actual.setRate(actualEntity.getRate());
                expected.setRate(expectedEntity.getRate());
            }
        }
        meterInfoDto.setCookie(Optional.ofNullable(expectedEntity.getCookie())
                .orElse(null));
        meterInfoDto.setBurstSize(Optional.ofNullable(actualEntity)
                .map(MeterInfoEntryV2::getBurstSize)
                .orElse(data.getExpected().getBurstSize()));
        meterInfoDto.setFlags((Optional.ofNullable(actualEntity)
                .map(MeterInfoEntryV2::getFlags)
                .orElse(expectedEntity.getFlags())).toArray(new String[0]));
        meterInfoDto.setRate(Optional.ofNullable(actualEntity)
                .map(MeterInfoEntryV2::getRate)
                .orElse(data.getExpected().getRate()));
        meterInfoDto.setActual(actual);
        meterInfoDto.setExpected(expected);

        return meterInfoDto;
    }

    /**
     * Convert api v2 rule validation entry into v1 rule dto.
     *
     * @param data rule v2 validation data.
     * @return rule v1 validation dto
     */
    public RulesValidationDto toRulesValidationDtoV1(RulesValidationEntryV2 data) {
        if (data == null) {
            return null;
        }
        RulesValidationDto rulesValidationDto = new RulesValidationDto();
        rulesValidationDto.setExcess(data.getExcess().stream()
                .map(RuleInfoEntryV2::getCookie).collect(Collectors.toList()));
        rulesValidationDto.setProper(data.getProper().stream()
                .map(RuleInfoEntryV2::getCookie).collect(Collectors.toList()));
        rulesValidationDto.setMissing(data.getMissing().stream()
                .map(RuleInfoEntryV2::getCookie).collect(Collectors.toList()));
        rulesValidationDto.setMisconfigured(data.getMisconfigured().stream()
                .map(MisconfiguredInfo::getExpected)
                .map(RuleInfoEntryV2::getCookie)
                .collect(Collectors.toList()));

        return rulesValidationDto;
    }

    //v2 api
    public abstract SwitchValidationResultV2 toSwitchValidationResultV2(SwitchValidationResponseV2 response);

    public abstract GroupsValidationDtoV2 toGroupsValidationDtoV2(GroupsValidationEntryV2 data);

    public abstract LogicalPortsValidationDtoV2 toLogicalPortsValidationDtoV2(LogicalPortsValidationEntryV2 data);

    public abstract MetersValidationDtoV2 toMetersValidationDtoV2(MetersValidationEntryV2 data);

    public abstract RulesValidationDtoV2 toRulesValidationDtoV2(RulesValidationEntryV2 data);

    public abstract RuleInfoDtoV2 toRuleInfoDtoV2(RuleInfoEntryV2 data);

    public abstract GroupInfoDtoV2 toGroupInfoDtoV2(GroupInfoEntryV2 data);

    private IncludeFilter toIncludeFilter(String value) {
        switch (value.toLowerCase()) {
            case ("meters"):
                return IncludeFilter.METERS;
            case ("groups"):
                return IncludeFilter.GROUPS;
            case ("logical_ports"):
                return IncludeFilter.LOGICAL_PORTS;
            case ("rules"):
                return IncludeFilter.RULES;
            default:
                throw new IllegalArgumentException(String.format("Unexpected include filter (%s)"
                        + "possible values are: \"meters\",\"groups\",\"rules\", \"logical_ports\"", value));
        }
    }

    private ExcludeFilter toExcludeFilter(String value) {
        switch (value.toLowerCase()) {
            case ("flow_info"):
                return ExcludeFilter.FLOW_INFO;
            default:
                throw new IllegalArgumentException(String.format("Unexpected exclude filter (%s), "
                        + "possible values are: \"flow_info\"", value));
        }
    }

    /**
     * Convert list of {@link String} into list of {@link IncludeFilter}.
     */
    public Set<IncludeFilter> toIncludeFilters(List<String> value) {
        return value.stream().map(this::toIncludeFilter).collect(Collectors.toSet());
    }

    public Set<ExcludeFilter> toExcludeFilters(List<String> value) {
        return value.stream().map(this::toExcludeFilter).collect(Collectors.toSet());
    }

    public abstract MeterInfoDtoV2 toMeterInfoDtoV2(MeterInfoEntryV2 data);

    public abstract LogicalPortInfoDtoV2 toLogicalPortInfoDtoV2(LogicalPortInfoEntryV2 data);

    //TODO(vshakirova): mapstruct could be bumped to >=1.5.0 and SubclassMapping annotation could replace instanceof
    org.openkilda.northbound.dto.v2.action.BaseAction toAction(BaseAction action) {
        if (action instanceof CopyFieldActionEntry) {
            return toCopyFieldActionDto((CopyFieldActionEntry) action);
        }
        if (action instanceof GroupActionEntry) {
            return toGroupActionDto((GroupActionEntry) action);
        }
        if (action instanceof MeterActionEntry) {
            return toMeterActionDto((MeterActionEntry) action);
        }
        if (action instanceof PopVlanActionEntry) {
            return toPopVlanActionDto((PopVlanActionEntry) action);
        }
        if (action instanceof PopVxlanActionEntry) {
            return toPopVxlanActionDto((PopVxlanActionEntry) action);
        }
        if (action instanceof PortOutActionEntry) {
            return toPortOutActionDto((PortOutActionEntry) action);
        }
        if (action instanceof PushVlanActionEntry) {
            return toPushVlanActionDto((PushVlanActionEntry) action);
        }
        if (action instanceof PushVxlanActionEntry) {
            return toPushVxlanActionDto((PushVxlanActionEntry) action);
        }
        if (action instanceof SetFieldActionEntry) {
            return toSetFieldActionDto((SetFieldActionEntry) action);
        }
        if (action instanceof SwapFieldActionEntry) {
            return toSwapFieldActionDto((SwapFieldActionEntry) action);
        }
        return null;
    }

    public abstract CopyFieldActionDto toCopyFieldActionDto(CopyFieldActionEntry data);

    public abstract GroupActionDto toGroupActionDto(GroupActionEntry data);

    public abstract MeterActionDto toMeterActionDto(MeterActionEntry data);

    public abstract PopVlanActionDto toPopVlanActionDto(PopVlanActionEntry data);

    public abstract PopVxlanActionDto toPopVxlanActionDto(PopVxlanActionEntry data);

    public abstract PortOutActionDto toPortOutActionDto(PortOutActionEntry data);

    public abstract PushVlanActionDto toPushVlanActionDto(PushVlanActionEntry data);

    public abstract PushVxlanActionDto toPushVxlanActionDto(PushVxlanActionEntry data);

    public abstract SetFieldActionDto toSetFieldActionDto(SetFieldActionEntry data);

    public abstract SwapFieldActionDto toSwapFieldActionDto(SwapFieldActionEntry data);
}
