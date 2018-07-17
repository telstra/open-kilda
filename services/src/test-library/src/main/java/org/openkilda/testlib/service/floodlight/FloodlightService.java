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

package org.openkilda.testlib.service.floodlight;

import org.openkilda.testlib.model.controller.CoreFlowEntry;
import org.openkilda.testlib.model.controller.DpIdEntriesList;
import org.openkilda.testlib.model.controller.StaticFlowEntry;
import org.openkilda.testlib.service.floodlight.model.FlowEntriesMap;
import org.openkilda.testlib.service.floodlight.model.MetersEntriesMap;
import org.openkilda.testlib.service.floodlight.model.SwitchEntry;

import java.util.List;

public interface FloodlightService {

    String addStaticFlow(StaticFlowEntry flow);

    String getAliveStatus();

    List<CoreFlowEntry> getCoreFlows(String dpId);

    DpIdEntriesList getStaticEntries(String dpId);

    List<SwitchEntry> getSwitches();

    FlowEntriesMap getFlows(String dpid);

    MetersEntriesMap getMeters(String dpid);
}
