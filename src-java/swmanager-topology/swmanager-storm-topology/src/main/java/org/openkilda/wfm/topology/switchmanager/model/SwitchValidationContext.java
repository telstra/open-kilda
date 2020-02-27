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

package org.openkilda.wfm.topology.switchmanager.model;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
public class SwitchValidationContext {
    private final SwitchId switchId;

    private final List<FlowSegmentRequestFactory> expectedFlowSegments;
    private final List<FlowEntry> expectedDefaultOfFlows;

    private final List<FlowEntry> actualOfFlows;
    private final List<MeterEntry> actualMeters;

    private final ValidateRulesResult ofFlowsValidationReport;
    private final ValidateMetersResult metersValidationReport;

    @Builder(toBuilder = true)
    protected SwitchValidationContext(
            SwitchId switchId, List<FlowSegmentRequestFactory> expectedFlowSegments,
            List<FlowEntry> expectedDefaultOfFlows, List<FlowEntry> actualOfFlows, List<MeterEntry> actualMeters,
            ValidateRulesResult ofFlowsValidationReport, ValidateMetersResult metersValidationReport) {
        this.switchId = switchId;

        this.expectedFlowSegments = expectedFlowSegments != null ? ImmutableList.copyOf(expectedFlowSegments) : null;
        this.expectedDefaultOfFlows = expectedDefaultOfFlows != null
                ? ImmutableList.copyOf(expectedDefaultOfFlows) : null;

        this.actualOfFlows = actualOfFlows != null ? ImmutableList.copyOf(actualOfFlows) : null;
        this.actualMeters = actualMeters != null ? ImmutableList.copyOf(actualMeters) : null;

        this.ofFlowsValidationReport = ofFlowsValidationReport;
        this.metersValidationReport = metersValidationReport;
    }

    public static SwitchValidationContextBuilder builder(SwitchId switchId) {
        return new SwitchValidationContextBuilder()
                .switchId(switchId);
    }
}
