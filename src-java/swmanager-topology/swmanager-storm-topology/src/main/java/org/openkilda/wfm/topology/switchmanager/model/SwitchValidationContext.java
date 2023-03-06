/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.model;

import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateGroupsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateLogicalPortsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateMetersResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateRulesResultV2;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
public class SwitchValidationContext {

    SwitchId switchId;
    List<FlowSpeakerData> actualOfFlows;

    List<MeterSpeakerData> actualMeters;

    List<GroupSpeakerData> actualGroupEntries;
    List<LogicalPort> actualLogicalPortEntries;
    List<SpeakerData> expectedSwitchEntities;

    ValidateRulesResultV2 ofFlowsValidationReport;
    ValidateMetersResultV2 metersValidationReport;
    ValidateGroupsResultV2 validateGroupsResult;
    ValidateLogicalPortsResultV2 validateLogicalPortResult;

    @Builder(toBuilder = true)
    protected SwitchValidationContext(
            SwitchId switchId, List<FlowSpeakerData> actualOfFlows,
            List<MeterSpeakerData> actualMeters, List<GroupSpeakerData> actualGroupEntries,
            List<LogicalPort> actualLogicalPortEntries,
            List<SpeakerData> expectedSwitchEntities,
            ValidateRulesResultV2 ofFlowsValidationReport, ValidateMetersResultV2 metersValidationReport,
            ValidateGroupsResultV2 validateGroupsResult, ValidateLogicalPortsResultV2 validateLogicalPortResult) {
        this.switchId = switchId;

        this.actualOfFlows = actualOfFlows != null ? ImmutableList.copyOf(actualOfFlows) : null;

        this.actualMeters = actualMeters != null ? ImmutableList.copyOf(actualMeters) : null;

        this.actualGroupEntries = actualGroupEntries != null ? ImmutableList.copyOf(actualGroupEntries) : null;
        this.actualLogicalPortEntries = actualLogicalPortEntries != null
                ? ImmutableList.copyOf(actualLogicalPortEntries) : null;
        this.expectedSwitchEntities = expectedSwitchEntities != null
                ? ImmutableList.copyOf(expectedSwitchEntities) : null;
        this.ofFlowsValidationReport = ofFlowsValidationReport;
        this.metersValidationReport = metersValidationReport;
        this.validateGroupsResult = validateGroupsResult;
        this.validateLogicalPortResult = validateLogicalPortResult;
    }

    public static SwitchValidationContextBuilder builder(SwitchId switchId) {
        return new SwitchValidationContextBuilder()
                .switchId(switchId);
    }
}
