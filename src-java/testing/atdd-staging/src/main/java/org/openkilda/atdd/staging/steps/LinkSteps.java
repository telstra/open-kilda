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

package org.openkilda.atdd.staging.steps;

import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.staging.helpers.TopologyUnderTest;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;
import org.openkilda.testing.service.northbound.NorthboundService;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assume;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Random;

@Slf4j
public class LinkSteps {

    @Autowired @Qualifier("northboundServiceImpl")
    private NorthboundService northboundService;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    @Autowired
    private TopologyDefinition topologyDefinition;

    private List<IslInfoData> linksResponse;

    @When("^request all available links from Northbound$")
    public void requestAllAvailableLinksFromNorthbound() {
        linksResponse = northboundService.getAllLinks();
    }

    @Then("^response has at least (\\d+) links?$")
    public void responseHasAtLeastLink(int linksAmount) {
        assertTrue(linksResponse.size() >= linksAmount);
    }

    @Given("^select a random ISL and alias it as '(.*)'$")
    public void selectARandomIslAndAliasItAsIsl(String islAlias) {
        List<Isl> isls = getUnaliasedIsls();
        Random r = new Random();
        Isl theIsl = isls.get(r.nextInt(isls.size()));
        log.info("Selected random isl: {}", theIsl.toString());
        topologyUnderTest.addAlias(islAlias, theIsl);
    }

    private List<Isl> getUnaliasedIsls() {
        List<Isl> aliasedIsls = topologyUnderTest.getAliasedObjects(Isl.class);
        List<Isl> isls = (List<Isl>) CollectionUtils.subtract(
                topologyDefinition.getIslsForActiveSwitches(), aliasedIsls);
        Assume.assumeTrue("No unaliased isls left, unable to proceed", !isls.isEmpty());
        return isls;
    }
}
