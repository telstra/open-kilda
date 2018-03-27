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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java8.En;
import org.apache.commons.collections4.CollectionUtils;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Isl;
import org.openkilda.atdd.staging.service.floodlight.FloodlightService;
import org.openkilda.atdd.staging.service.floodlight.model.FlowEntriesMap;
import org.openkilda.atdd.staging.service.floodlight.model.SwitchEntry;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.steps.helpers.DefaultFlowsChecker;
import org.openkilda.atdd.staging.steps.helpers.TopologyChecker.IslMatcher;
import org.openkilda.atdd.staging.steps.helpers.TopologyChecker.SwitchEntryMatcher;
import org.openkilda.atdd.staging.steps.helpers.TopologyChecker.SwitchMatcher;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Stream;

public class DiscoveryMechanismSteps implements En {

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private TopologyEngineService topologyEngineService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    private Scenario scenario;

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
    }

    @Then("^all provided switches should be discovered")
    public void checkDiscoveredSwitches() {
        List<SwitchInfoData> discoveredSwitches = topologyEngineService.getActiveSwitches();
        assertFalse("No switches were discovered", CollectionUtils.isEmpty(discoveredSwitches));

        List<TopologyDefinition.Switch> expectedSwitches = topologyDefinition.getActiveSwitches();
        assertFalse("Expected switches should be provided", expectedSwitches.isEmpty());

        assertThat("Discovered switches don't match expected", discoveredSwitches, containsInAnyOrder(
                expectedSwitches.stream().map(SwitchMatcher::new).collect(toList())));
    }

    @Then("^all provided links should be detected")
    public void checkDiscoveredLinks() {
        List<IslInfoData> discoveredLinks = topologyEngineService.getActiveLinks();
        List<TopologyDefinition.Isl> expectedLinks = topologyDefinition.getIslsForActiveSwitches();

        if (CollectionUtils.isEmpty(discoveredLinks) && expectedLinks.isEmpty()) {
            scenario.write("There are no links discovered as expected");
            return;
        }

        assertThat("Discovered links don't match expected", discoveredLinks, containsInAnyOrder(
                expectedLinks.stream()
                        .flatMap(link -> {
                            //in kilda we have forward and reverse isl, that's why we have to divide into 2
                            Isl pairedLink = Isl.factory(link.getDstSwitch(), link.getDstPort(),
                                    link.getSrcSwitch(), link.getSrcPort(), link.getMaxBandwidth());
                            return Stream.of(link, pairedLink);
                        })
                        .map(IslMatcher::new)
                        .collect(toList())));
    }

    @Then("^floodlight should not find redundant switches")
    public void checkFloodlightSwitches() {
        List<SwitchEntry> floodlightSwitches = floodlightService.getSwitches();
        List<TopologyDefinition.Switch> expectedSwitches = topologyDefinition.getActiveSwitches();

        assertThat("Discovered switches don't match expected", floodlightSwitches, containsInAnyOrder(
                expectedSwitches.stream().map(SwitchEntryMatcher::new).collect(toList())));
    }

    @Then("^default rules for switches are installed")
    public void checkDefaultRules() {
        List<SwitchEntry> switches = floodlightService.getSwitches();

        List<SwitchEntry> switchesWithInvalidFlows = switches.stream()
                .filter(sw -> {
                    FlowEntriesMap flows = floodlightService.getFlows(sw.getSwitchId());
                    return !DefaultFlowsChecker.validateDefaultRules(sw, flows, scenario);
                })
                .collect(toList());
        assertTrue("There were found switches with incorrect default flows",
                switchesWithInvalidFlows.isEmpty());
    }
}
