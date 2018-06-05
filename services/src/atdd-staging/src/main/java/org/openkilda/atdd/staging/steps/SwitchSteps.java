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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Isl;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Switch;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.steps.helpers.TopologyUnderTest;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SwitchSteps {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchSteps.class);

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    @When("^request all available switches from Northbound$")
    public void requestSwitches() {
        topologyUnderTest.setResponse(northboundService.getAllSwitches());
    }

    @Then("response has at least (\\d+) switch(?:es)?")
    public void verifySwitchesAmount(int expectedSwitchesAmount) {
        List<SwitchInfoData> response = (List<SwitchInfoData>) topologyUnderTest.getResponse();
        assertTrue(response.size() >= expectedSwitchesAmount);
    }

    @Given("^select a random switch and alias it as '(.*)'$")
    public void selectARandomSwitch(String switchAlias) {
        List<Switch> switches = getUnaliasedSwitches();
        Random r = new Random();
        Switch theSwitch = switches.get(r.nextInt(switches.size()));
        LOGGER.info("Selected random switch with id: {}", theSwitch.getDpId());
        topologyUnderTest.getAliasedObjects().put(switchAlias, theSwitch);
    }

    @When("^request all switch rules for switch '(.*)'$")
    public void requestAllSwitchRulesForSwitch(String switchAlias) {
        topologyUnderTest.setResponse(northboundService.getSwitchRules(
                ((Switch) topologyUnderTest.getAliasedObjects().get(switchAlias)).getDpId()));
    }

    @Then("^response switch_id matches id of '(.*)'$")
    public void responseSwitch_idMatchesIdOfSwitch(String switchAlias) {
        SwitchFlowEntries response = (SwitchFlowEntries) topologyUnderTest.getResponse();
        assertEquals(((Switch) topologyUnderTest.getAliasedObjects().get(switchAlias)).getDpId(),
                response.getSwitchId());
    }

    @Then("^response has at least (\\d+) rules? installed$")
    public void responseHasAtLeastRulesInstalled(int rulesAmount) {
        SwitchFlowEntries response = (SwitchFlowEntries) topologyUnderTest.getResponse();
        assertTrue(response.getFlowEntries().size() >= rulesAmount);
    }

    @And("^select a switch with direct link to '(.*)' and alias it as '(.*)'$")
    public void selectSwitchWithDirectLink(String nearSwitchAlias, String newSwitchAlias) {
        Switch nearSwitch = (Switch) topologyUnderTest.getAliasedObjects().get(newSwitchAlias);
        Isl link = topologyDefinition.getIslsForActiveSwitches().stream().filter(isl ->
                isl.getSrcSwitch().getDpId().equals(nearSwitch.getDpId())
                        || isl.getDstSwitch().getDpId().equals(nearSwitch.getDpId())).findAny().get();
        Switch newSwitch = link.getSrcSwitch().getDpId().equals(nearSwitch.getDpId())
                ? link.getDstSwitch() : link.getDstSwitch();
        topologyUnderTest.getAliasedObjects().put(newSwitchAlias, newSwitch);
    }

    private List<Switch> getUnaliasedSwitches() {
        List<Switch> aliasedSwitches = topologyUnderTest.getAliasedObjects().values().stream()
                .filter(obj -> obj instanceof Switch)
                .map(sw -> (Switch) sw).collect(Collectors.toList());
        List<Switch> switches = (List<Switch>) CollectionUtils.subtract(
                topologyDefinition.getActiveSwitches(), aliasedSwitches);
        Assume.assumeTrue("No unaliased switches left, unable to proceed", !switches.isEmpty());
        return switches;
    }
}
