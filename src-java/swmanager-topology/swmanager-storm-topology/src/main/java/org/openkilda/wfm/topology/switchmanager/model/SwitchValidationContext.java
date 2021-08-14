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

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
public class SwitchValidationContext {
    private final SwitchId switchId;

    List<FlowSegmentRequestFactory> expectedFlowSegments;
    List<FlowEntry> expectedServiceOfFlows;
    List<FlowEntry> actualOfFlows;

    List<MeterEntry> expectedServiceMeters;
    List<MeterEntry> actualMeters;

    List<GroupEntry> actualGroupEntries;
    List<LogicalPort> actualLogicalPortEntries;

    ValidateRulesResult ofFlowsValidationReport;
    ValidateMetersResult metersValidationReport;
    ValidateGroupsResult validateGroupsResult;
    ValidateLogicalPortsResult validateLogicalPortResult;

    @Builder(toBuilder = true)
    protected SwitchValidationContext(
            SwitchId switchId, List<FlowSegmentRequestFactory> expectedFlowSegments,
            List<FlowEntry> expectedServiceOfFlows, List<FlowEntry> actualOfFlows,
            List<MeterEntry> expectedServiceMeters, List<MeterEntry> actualMeters,
            List<GroupEntry> actualGroupEntries,
            List<LogicalPort> actualLogicalPortEntries,
            ValidateRulesResult ofFlowsValidationReport, ValidateMetersResult metersValidationReport,
            ValidateGroupsResult validateGroupsResult, ValidateLogicalPortsResult validateLogicalPortResult) {
        this.switchId = switchId;

        this.expectedFlowSegments = expectedFlowSegments != null ? ImmutableList.copyOf(expectedFlowSegments) : null;
        this.expectedServiceOfFlows = expectedServiceOfFlows != null
                ? ImmutableList.copyOf(expectedServiceOfFlows) : null;
        this.actualOfFlows = actualOfFlows != null ? ImmutableList.copyOf(actualOfFlows) : null;

        this.expectedServiceMeters = expectedServiceMeters != null ? ImmutableList.copyOf(expectedServiceMeters) : null;
        this.actualMeters = actualMeters != null ? ImmutableList.copyOf(actualMeters) : null;

        this.actualGroupEntries = actualGroupEntries != null ? ImmutableList.copyOf(actualGroupEntries) : null;
        this.actualLogicalPortEntries = actualLogicalPortEntries != null
                ? ImmutableList.copyOf(actualLogicalPortEntries) : null;

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
