/* Copyright 2018 Telstra Open Source
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

package org.openkilda.testing.service.floodlight;

import org.openkilda.model.SwitchId;
import org.openkilda.testing.model.controller.CoreFlowEntry;
import org.openkilda.testing.model.controller.DpIdEntriesList;
import org.openkilda.testing.model.controller.StaticFlowEntry;
import org.openkilda.testing.service.floodlight.model.ControllerRole;
import org.openkilda.testing.service.floodlight.model.FlowEntriesMap;
import org.openkilda.testing.service.floodlight.model.MetersEntriesMap;
import org.openkilda.testing.service.floodlight.model.SwitchEntry;

import java.util.List;
import java.util.Map;

public interface FloodlightService {

    String addStaticFlow(StaticFlowEntry flow);

    String getAliveStatus();

    List<CoreFlowEntry> getCoreFlows(SwitchId dpId);

    DpIdEntriesList getStaticEntries(SwitchId dpId);

    List<SwitchEntry> getSwitches();

    FlowEntriesMap getFlows(SwitchId dpid);

    MetersEntriesMap getMeters(SwitchId dpid);

    Map setRole(SwitchId dpid, ControllerRole role);
}
