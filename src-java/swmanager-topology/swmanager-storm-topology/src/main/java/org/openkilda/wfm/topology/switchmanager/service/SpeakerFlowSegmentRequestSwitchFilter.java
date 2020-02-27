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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.model.FlowPathSnapshot;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SpeakerFlowSegmentRequestSwitchFilter implements FlowCommandBuilder {
    private final SwitchId switchId;
    private final FlowCommandBuilder target;

    public SpeakerFlowSegmentRequestSwitchFilter(SwitchId switchId, FlowCommandBuilder target) {
        this.switchId = switchId;
        this.target = target;
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAll(
            CommandContext context, Flow flow, FlowPathSnapshot path, FlowPathSnapshot oppositePath) {
        return applyFilter(target.buildAll(context, flow, path, oppositePath));
    }

    @Override
    public List<FlowSegmentRequestFactory> buildAllExceptIngress(
            CommandContext context, Flow flow, FlowPathSnapshot path, FlowPathSnapshot oppositePath) {
        return applyFilter(target.buildAllExceptIngress(context, flow, path, oppositePath));
    }

    @Override
    public List<FlowSegmentRequestFactory> buildIngressOnly(
            CommandContext context, Flow flow, FlowPathSnapshot path, FlowPathSnapshot oppositePath) {
        return applyFilter(target.buildIngressOnly(context, flow, path, oppositePath));
    }

    private List<FlowSegmentRequestFactory> applyFilter(List<FlowSegmentRequestFactory> original) {
        return original.stream()
                .filter(entry -> switchId.equals(entry.getSwitchId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
