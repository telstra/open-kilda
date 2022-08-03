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

import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.messaging.info.switches.GroupInfoEntry.BucketEntry;
import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
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
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateGroupsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateLogicalPortsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateMetersResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateRulesResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidationResultV2;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
@Slf4j
public abstract class ValidationMapper {
    public static final ValidationMapper INSTANCE = Mappers.getMapper(ValidationMapper.class);

    /**
     * Produce {@code SwitchValidationResponse} from {@code SwitchValidationContext}.
     */
    public SwitchValidationResponseV2 toSwitchResponse(SwitchValidationContext validationContext) {
        if (validationContext == null) {
            return null;
        }

        SwitchValidationResponseV2.SwitchValidationResponseV2Builder response = SwitchValidationResponseV2.builder();
        if (validationContext.getOfFlowsValidationReport() != null) {
            response.rules(mapReport(validationContext.getOfFlowsValidationReport()));
        }
        if (validationContext.getMetersValidationReport() != null) {
            response.meters(mapReport(validationContext.getMetersValidationReport()));
        }
        if (validationContext.getValidateGroupsResult() != null) {
            response.groups(mapReport(validationContext.getValidateGroupsResult()));
        }
        if (validationContext.getValidateLogicalPortResult() != null) {
            response.logicalPorts(mapReport(validationContext.getValidateLogicalPortResult()));
        }

        return response.build();
    }

    @Mapping(source = "missingRules", target = "missing")
    @Mapping(source = "properRules", target = "proper")
    @Mapping(source = "excessRules", target = "excess")
    @Mapping(source = "misconfiguredRules", target = "misconfigured")
    public abstract RulesValidationEntryV2 mapReport(ValidateRulesResultV2 report);

    @Mapping(source = "missingMeters", target = "missing")
    @Mapping(source = "misconfiguredMeters", target = "misconfigured")
    @Mapping(source = "properMeters", target = "proper")
    @Mapping(source = "excessMeters", target = "excess")
    public abstract MetersValidationEntryV2 mapReport(ValidateMetersResultV2 report);

    @Mapping(source = "missingGroups", target = "missing")
    @Mapping(source = "misconfiguredGroups", target = "misconfigured")
    @Mapping(source = "properGroups", target = "proper")
    @Mapping(source = "excessGroups", target = "excess")
    public abstract GroupsValidationEntryV2 mapReport(ValidateGroupsResultV2 report);

    @Mapping(source = "missingLogicalPorts", target = "missing")
    @Mapping(source = "misconfiguredLogicalPorts", target = "misconfigured")
    @Mapping(source = "properLogicalPorts", target = "proper")
    @Mapping(source = "excessLogicalPorts", target = "excess")
    @Mapping(source = "errorMessage", target = "error")
    public abstract LogicalPortsValidationEntryV2 mapReport(ValidateLogicalPortsResultV2 report);

    /**
     * Produce {@code ValidationResult} from {@code ValidationResultV2}.
     */
    public ValidationResult toValidationResult(ValidationResultV2 validationResultV2) {
        if (validationResultV2 == null) {
            return null;
        }

        return new ValidationResult(
                validationResultV2.getFlowEntries(), validationResultV2.isProcessMeters(),
                validationResultV2.getExpectedEntries(), validationResultV2.getActualFlows(),
                validationResultV2.getActualMeters(), validationResultV2.getActualGroups(),
                map(validationResultV2.getValidateRulesResult()), map(validationResultV2.getValidateMetersResult()),
                map(validationResultV2.getValidateGroupsResult()),
                map(validationResultV2.getValidateLogicalPortsResult())
        );
    }

    /**
     * Convert v2 rule representation into v1 rule representation.
     *
     * @param report v2 rule representation.
     * @return v1 rule representation.
     */
    public ValidateRulesResult map(ValidateRulesResultV2 report) {
        if (report == null) {
            return null;
        }

        Set<Long> proper = report.getProperRules().stream()
                .map(RuleInfoEntryV2::getCookie)
                .collect(Collectors.toSet());
        Set<Long> excess = report.getExcessRules().stream()
                .map(RuleInfoEntryV2::getCookie)
                .collect(Collectors.toSet());
        Set<Long> missing = report.getMissingRules().stream()
                .map(RuleInfoEntryV2::getCookie)
                .collect(Collectors.toSet());
        Set<Long> misconfigured = report.getMisconfiguredRules().stream()
                .map(m -> m.getDiscrepancies().getCookie())
                .collect(Collectors.toSet());

        return new ValidateRulesResult(missing, proper, excess, misconfigured);
    }

    public abstract ValidateMetersResult map(ValidateMetersResultV2 report);

    public abstract ValidateLogicalPortsResult map(ValidateLogicalPortsResultV2 report);

    @Mapping(target = "GroupInfoEntry.missingGroupBuckets", ignore = true)
    @Mapping(target = "GroupInfoEntry.excessGroupBuckets", ignore = true)
    public abstract ValidateGroupsResult map(ValidateGroupsResultV2 report);

    @Mapping(source = "buckets", target = "groupBuckets")
    @Mapping(target = "missingGroupBuckets", ignore = true)
    @Mapping(target = "excessGroupBuckets", ignore = true)
    public abstract GroupInfoEntry map(GroupInfoEntryV2 groupInfo);

    /**
     * Convert v2 misconfigured rule representation into v1 rule representation.
     *
     * @param groupInfo v2 rule representation.
     * @return v1 rule representation.
     */
    public GroupInfoEntry map(MisconfiguredInfo<GroupInfoEntryV2> groupInfo) {
        if (groupInfo == null) {
            return null;
        }
        List<BucketEntry> missingGroupBuckets = new ArrayList<>();
        List<BucketEntry> excessGroupBuckets = new ArrayList<>();
        List<BucketEntry> groupBuckets = groupInfo.getExpected().getBuckets().stream()
                .map(bucket -> new BucketEntry(bucket.getPort(), bucket.getVlan(), bucket.getVni()))
                .collect(Collectors.toList());

        groupInfo.getDiscrepancies().getBuckets().forEach(bucket -> {
                    BucketEntry b = new BucketEntry(bucket.getPort(), bucket.getVlan(), bucket.getVni());
                    if (groupInfo.getExpected().getBuckets().contains(bucket)) {
                        missingGroupBuckets.add(b);
                    } else {
                        excessGroupBuckets.add(b);
                    }
                }
        );
        return GroupInfoEntry.builder()
                .groupId(groupInfo.getId().intValue())
                .groupBuckets(groupBuckets)
                .missingGroupBuckets(missingGroupBuckets)
                .excessGroupBuckets(excessGroupBuckets)
                .build();
    }

    @Mapping(target = "expected", ignore = true)
    @Mapping(target = "actual", ignore = true)
    @Mapping(source = "logicalPortNumber", target = "logicalPortNumber")
    @Mapping(source = "type", target = "type")
    public abstract LogicalPortInfoEntry map(LogicalPortInfoEntryV2 report);

    @Mapping(source = "id", target = "logicalPortNumber")
    @Mapping(source = "expected.type", target = "type")
    @Mapping(source = "expected.physicalPorts", target = "physicalPorts")
    @Mapping(source = "expected.type", target = "expected.type")
    @Mapping(source = "expected.physicalPorts", target = "expected.physicalPorts")
    @Mapping(source = "discrepancies.type", target = "actual.type")
    @Mapping(source = "discrepancies.physicalPorts", target = "actual.physicalPorts")
    public abstract LogicalPortInfoEntry toLogicalPortInfoEntryV1(MisconfiguredInfo<LogicalPortInfoEntryV2> groupInfo);

    @Mapping(target = "expected", ignore = true)
    @Mapping(target = "actual", ignore = true)
    public abstract MeterInfoEntry toMeterInfoEntryV1(MeterInfoEntryV2 report);

    @Mapping(source = "id", target = "meterId")
    @Mapping(source = "expected.cookie", target = "cookie")
    @Mapping(source = "expected.flowId", target = "flowId")
    @Mapping(source = "expected.rate", target = "rate")
    @Mapping(source = "expected.burstSize", target = "burstSize")
    @Mapping(source = "expected.flags", target = "flags")
    @Mapping(source = "expected.rate", target = "expected.rate")
    @Mapping(source = "expected.burstSize", target = "expected.burstSize")
    @Mapping(source = "expected.flags", target = "expected.flags")
    @Mapping(source = "discrepancies.rate", target = "actual.rate")
    @Mapping(source = "discrepancies.burstSize", target = "actual.burstSize")
    @Mapping(source = "discrepancies.flags", target = "actual.flags")
    public abstract MeterInfoEntry toMeterInfoEntryV1(MisconfiguredInfo<MeterInfoEntryV2> groupInfo);
}
