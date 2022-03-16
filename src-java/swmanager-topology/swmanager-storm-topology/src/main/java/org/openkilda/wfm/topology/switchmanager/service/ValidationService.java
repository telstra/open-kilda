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
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;

import java.util.List;

public interface ValidationService {
    ValidateRulesResult validateRules(SwitchId switchId, List<FlowSpeakerData> presentRules,
                                      List<FlowSpeakerData> expectedRules);

    ValidateGroupsResult validateGroups(SwitchId switchId, List<GroupSpeakerData> presentGroups,
                                        List<GroupSpeakerData> expectedGroups);

    ValidateLogicalPortsResult validateLogicalPorts(SwitchId switchId, List<LogicalPort> presentLogicalPorts);

    ValidateMetersResult validateMeters(SwitchId switchId, List<MeterSpeakerData> presentMeters,
                                        List<MeterSpeakerData> expectedMeters);

    List<SpeakerData> buildExpectedEntities(SwitchId switchId);
}
