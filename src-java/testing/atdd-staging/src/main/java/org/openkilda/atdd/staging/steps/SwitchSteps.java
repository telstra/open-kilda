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

import org.openkilda.atdd.staging.helpers.TopologyUnderTest;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.northbound.NorthboundService;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assume;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

@Slf4j
public class SwitchSteps {

    @Autowired @Qualifier("northboundServiceImpl")
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    private List<SwitchDto> allSwitchesResponse;
    private SwitchFlowEntries switchRulesResponse;

    @When("^request all available switches from Northbound$")
    public void requestSwitches() {
        allSwitchesResponse = northboundService.getAllSwitches();
    }

    @Then("response has at least (\\d+) switch(?:es)?")
    public void verifySwitchesAmount(int expectedSwitchesAmount) {
        assertTrue(allSwitchesResponse.size() >= expectedSwitchesAmount);
    }

    @Given("^select a switch and alias it as '(.*)'$")
    public void selectARandomSwitch(String switchAlias) {
        List<Switch> switches = getUnaliasedSwitches();
        Assume.assumeFalse("All switches are already aliased", CollectionUtils.isEmpty(switches));
        Switch theSwitch = switches.get(0);
        log.info("Selected switch with id: {}", theSwitch.getDpId());
        topologyUnderTest.addAlias(switchAlias, theSwitch);
    }

    @When("^request all switch rules for switch '(.*)'$")
    public void requestAllSwitchRulesForSwitch(String switchAlias) {
        switchRulesResponse = northboundService.getSwitchRules(
                ((Switch) topologyUnderTest.getAliasedObject(switchAlias)).getDpId());
    }

    @Then("^response switch_id matches id of '(.*)'$")
    public void responseSwitch_idMatchesIdOfSwitch(String switchAlias) {
        assertEquals(((Switch) topologyUnderTest.getAliasedObject(switchAlias)).getDpId(),
                switchRulesResponse.getSwitchId());
    }

    @And("^response has at least (\\d+) rules? installed$")
    public void responseHasAtLeastRulesInstalled(int rulesAmount) {
        assertTrue(switchRulesResponse.getFlowEntries().size() >= rulesAmount);
    }

    private List<Switch> getUnaliasedSwitches() {
        List<Switch> aliasedSwitches = topologyUnderTest.getAliasedObjects(Switch.class);
        List<Switch> switches = (List<Switch>) CollectionUtils.subtract(
                topologyDefinition.getActiveSwitches(), aliasedSwitches);
        Assume.assumeTrue("No unaliased switches left, unable to proceed", !switches.isEmpty());
        return switches;
    }
}
