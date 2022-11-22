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

package org.openkilda.wfm.topology.switchmanager.service;

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

import java.util.List;

public interface ValidationService {
    ValidateRulesResultV2 validateRules(SwitchId switchId, List<FlowSpeakerData> presentRules,
                                        List<FlowSpeakerData> expectedRules, boolean includeFlowInfo);

    ValidateGroupsResultV2 validateGroups(SwitchId switchId, List<GroupSpeakerData> presentGroups,
                                          List<GroupSpeakerData> expectedGroups, boolean includeFlowInfo);

    ValidateLogicalPortsResultV2 validateLogicalPorts(SwitchId switchId, List<LogicalPort> presentLogicalPorts);

    ValidateMetersResultV2 validateMeters(
            SwitchId switchId, List<MeterSpeakerData> presentMeters, List<MeterSpeakerData> expectedMeters,
            boolean includeAllFlowInfo, boolean includeMeterFlowInfo);

    List<SpeakerData> buildExpectedEntities(SwitchId switchId);
}
