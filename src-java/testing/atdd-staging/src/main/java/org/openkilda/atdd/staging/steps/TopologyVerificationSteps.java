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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.staging.helpers.DefaultFlowsChecker;
import org.openkilda.atdd.staging.helpers.TopologyChecker.IslMatcher;
import org.openkilda.atdd.staging.helpers.TopologyChecker.SwitchEntryMatcher;
import org.openkilda.atdd.staging.helpers.TopologyChecker.SwitchMatcher;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;
import org.openkilda.testing.service.floodlight.FloodlightService;
import org.openkilda.testing.service.floodlight.model.FlowEntriesMap;
import org.openkilda.testing.service.floodlight.model.SwitchEntry;
import org.openkilda.testing.service.northbound.NorthboundService;

import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java8.En;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TopologyVerificationSteps implements En {

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired @Qualifier("northboundServiceImpl")
    private NorthboundService northboundService;

    private List<TopologyDefinition.Switch> referenceSwitches;
    private List<TopologyDefinition.Isl> referenceLinks;
    private List<SwitchDto> actualSwitches;
    private List<IslInfoData> actualLinks;

    private Scenario scenario;

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
    }

    @Given("^the reference topology$")
    public void checkTheTopology() {
        Set<SwitchId> skippedSwitches = topologyDefinition.getSkippedSwitchIds();

        referenceSwitches = topologyDefinition.getActiveSwitches();
        actualSwitches = northboundService.getActiveSwitches().stream()
                .filter(sw -> !skippedSwitches.contains(sw.getSwitchId()))
                .collect(toList());

        referenceLinks = topologyDefinition.getIslsForActiveSwitches();
        actualLinks = northboundService.getActiveLinks().stream()
                .filter(sw -> !skippedSwitches.contains(sw.getSource().getSwitchId()))
                .filter(sw -> !skippedSwitches.contains(sw.getDestination().getSwitchId()))
                .collect(Collectors.toList());
    }


    @And("^all defined switches are discovered")
    public void checkDiscoveredSwitches() {
        assertFalse("No switches were discovered", actualSwitches.isEmpty());

        assertThat("Discovered switches don't match expected", actualSwitches, containsInAnyOrder(
                referenceSwitches.stream().map(SwitchMatcher::new).collect(toList())));

    }

    @And("^all defined links are detected")
    public void checkDiscoveredLinks() {
        if (actualLinks.isEmpty() && referenceLinks.isEmpty()) {
            scenario.write("There are no links discovered as expected");
            return;
        }

        assertFalse("No links were discovered", actualLinks.isEmpty());

        assertThat("Discovered links don't match expected", actualLinks, containsInAnyOrder(
                referenceLinks.stream()
                        .flatMap(link -> {
                            //in kilda we have forward and reverse isl, that's why we have to divide into 2
                            Isl pairedLink = Isl.factory(link.getDstSwitch(), link.getDstPort(), link.getSrcSwitch(),
                                    link.getSrcPort(), link.getMaxBandwidth(), link.getAswitch());
                            return Stream.of(link, pairedLink);
                        })
                        .map(IslMatcher::new)
                        .collect(toList())));
    }

    @And("^all active switches have correct rules installed per Northbound validation")
    public void validateSwitchRules() {
        actualSwitches.forEach(sw -> {
            SwitchId switchId = sw.getSwitchId();
            RulesValidationResult validationResult = northboundService.validateSwitchRules(switchId);
            assertTrue(format("The switch '%s' is missing rules: %s", switchId, validationResult.getMissingRules()),
                    validationResult.getMissingRules().isEmpty());
            assertTrue(format("The switch '%s' has excess rules: %s", switchId, validationResult.getExcessRules()),
                    validationResult.getExcessRules().isEmpty());
        });
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
