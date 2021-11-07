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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.model.SwitchId;

import java.util.List;
import java.util.Set;

public interface CommandBuilder {

    List<RemoveFlow> buildCommandsToRemoveExcessRules(SwitchId switchId,
                                                      List<FlowEntry> flows,
                                                      Set<Long> excessRulesCookies);

    List<CreateLogicalPortRequest> buildLogicalPortInstallCommands(
            SwitchId switchId, List<LogicalPortInfoEntry> missingLogicalPorts);

    List<DeleteLogicalPortRequest> buildLogicalPortDeleteCommands(SwitchId switchId, List<Integer> excessLogicalPorts);
}
