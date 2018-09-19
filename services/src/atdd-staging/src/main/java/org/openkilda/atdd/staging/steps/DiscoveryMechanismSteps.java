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

package org.openkilda.atdd.staging.steps;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.staging.helpers.DefaultFlowsChecker;
import org.openkilda.atdd.staging.helpers.TopologyChecker.SwitchEntryMatcher;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.service.floodlight.FloodlightService;
import org.openkilda.testing.service.floodlight.model.FlowEntriesMap;
import org.openkilda.testing.service.floodlight.model.SwitchEntry;

import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java8.En;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

public class DiscoveryMechanismSteps implements En {

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    private Scenario scenario;

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
    }

    @Then("^floodlight should not find redundant switches")
    public void checkFloodlightSwitches() {
        List<TopologyDefinition.Switch> expectedSwitches = topologyDefinition.getActiveSwitches();
        List<SwitchEntry> floodlightSwitches = fetchFloodlightSwitches();

        assertThat("Discovered switches don't match expected", floodlightSwitches, containsInAnyOrder(
                expectedSwitches.stream().map(SwitchEntryMatcher::new).collect(toList())));
    }

    @Then("^default rules for switches are installed")
    public void checkDefaultRules() {
        List<SwitchEntry> floodlightSwitches = fetchFloodlightSwitches();

        List<SwitchEntry> switchesWithInvalidFlows = floodlightSwitches.stream()
                .filter(sw -> {
                    FlowEntriesMap flows = floodlightService.getFlows(sw.getSwitchId());
                    return !DefaultFlowsChecker.validateDefaultRules(sw, flows, scenario);
                })
                .collect(toList());
        assertTrue("There were found switches with incorrect default flows",
                switchesWithInvalidFlows.isEmpty());
    }

    private List<SwitchEntry> fetchFloodlightSwitches() {
        Set<SwitchId> skippedSwitches = topologyDefinition.getSkippedSwitchIds();

        return floodlightService.getSwitches().stream()
                .filter(sw -> !skippedSwitches.contains(sw.getSwitchId()))
                .collect(toList());
    }
}
