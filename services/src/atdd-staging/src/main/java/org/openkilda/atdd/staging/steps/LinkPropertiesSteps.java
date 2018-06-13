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

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Isl;
import org.openkilda.atdd.staging.service.neo4j.Neo4jDriverFactoryImpl;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.steps.helpers.TopologyUnderTest;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.links.LinkPropsDto;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class LinkPropertiesSteps {

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    @Autowired
    private Neo4jDriverFactoryImpl neo4j;

    private LinkPropsDto linkPropsRequest;
    private BatchResults changePropsResponse;
    private List<LinkPropsDto> getLinkPropsResponse;


    @When("^create link properties request for isl '(.*)'$")
    public void createLinkPropertiesRequest(String islAlias) {
        linkPropsRequest = new LinkPropsDto();
        Isl theIsl = topologyUnderTest.getAliasedObject(islAlias);
        linkPropsRequest.setSrcSwitch(theIsl.getSrcSwitch().getDpId());
        linkPropsRequest.setSrcPort(theIsl.getSrcPort());
        linkPropsRequest.setDstSwitch(theIsl.getDstSwitch().getDpId());
        linkPropsRequest.setDstPort(theIsl.getDstPort());
    }

    @When("^update request: add link property '(.*)' with value '(.*)'$")
    public void updateRequestProperty(String key, String value) {
        linkPropsRequest.setProperty(key, value);
    }

    @When("^send update link properties request$")
    public void sendUpdateLinkPropertiesRequest() {
        changePropsResponse = northboundService.updateLinkProps(singletonList(linkPropsRequest));
    }

    @Then("^response has (\\d+) failures? and (\\d+) success(?:es)?$")
    public void responseHasFailuresAndSuccess(int failures, int successes) {
        assertEquals(changePropsResponse.getFailures(), failures);
        assertEquals(changePropsResponse.getSuccesses(), successes);
    }

    @When("^get all properties$")
    public void getAllProperties() {
        getLinkPropsResponse = northboundService.getLinkProps(null, null, null, null);
    }

    @Then("^response has( no)? link properties from request$")
    public void responseHasLinkPropertiesEntry(String shouldHaveStr) {
        final boolean shouldHave = shouldHaveStr == null;
        Optional<LinkPropsDto> wantedProps = getLinkPropsResponse.stream()
                .filter(props -> props.equals(linkPropsRequest)).findFirst();
        assertEquals(wantedProps.isPresent(), shouldHave);
    }

    @Then("^response link properties from request has property '(.*)' with value '(.*)'$")
    public void verifyResponseLinkProperties(String key, String value) {
        LinkPropsDto props = getLinkPropsResponse.stream()
                .filter(p -> p.equals(linkPropsRequest)).findFirst().get();
        assertThat(props.getProperty(key), equalTo(value));
    }

    @When("^send delete link properties request$")
    public void sendDeleteLinkPropertiesRequest() {
        changePropsResponse = northboundService.deleteLinkProps(singletonList(linkPropsRequest));
    }

    @And("^requested link property in Neo4j has( no)? property '(.*)' with value '(.*)'$")
    public void verifyLinkPropertyInNeo(String shouldHaveStr, String key, String value) {
        final boolean shouldHave = shouldHaveStr == null;
        Driver neo = neo4j.getDriver();
        String query = "MATCH ()-[link:isl {src_port:{srcPort}, dst_port:{dstPort}, src_switch:{srcSwitch}, "
                + "dst_switch:{dstSwitch}}]-() RETURN properties(link)";
        Map<String, Object> params = new HashMap<>();
        params.put("srcPort", linkPropsRequest.getSrcPort());
        params.put("dstPort", linkPropsRequest.getDstPort());
        params.put("srcSwitch", linkPropsRequest.getSrcSwitch());
        params.put("dstSwitch", linkPropsRequest.getDstSwitch());
        StatementResult result;
        try (Session session = neo.session()) {
            result = session.run(query, params);
        }
        Map props = result.list().get(0).get("properties(link)").asMap();
        if (shouldHave) {
            assertEquals(props.get(key), value);
        } else {
            assertFalse(props.containsKey(key));
        }
    }

    @Then("^remove all '(.*)' link properties from ISLs in Neo4j$")
    public void removeLinkPropertyNeo4j(String key) {
        Driver neo = neo4j.getDriver();
        String query = String.format("match ()-[link:isl]-() where exists(link.%1$s) remove link.%1$s", key);
        try (Session session = neo.session()) {
            session.run(query);
        }
    }

    @Then("^link props response has (\\d+) results?$")
    public void responseHasResults(int resultsAmount) {
        assertEquals(getLinkPropsResponse.size(), resultsAmount);
    }

    @When("^update request: change src_switch to '(.*)'$")
    public void updateRequestChangeSrcSwitch(String newSrcSwitch) {
        linkPropsRequest.setSrcSwitch(newSrcSwitch);
    }

    @And("^update request: change src_port to '(.*)'$")
    public void updateRequestChangeSrc_portTo(String newSrcPort) {
        linkPropsRequest.setSrcPort(Integer.valueOf(newSrcPort));
    }

    @When("^create empty link properties request$")
    public void createEmptyLinkPropertiesRequest() {
        linkPropsRequest = new LinkPropsDto();
    }

    @And("^get link properties for defined request$")
    public void getLinkPropertiesForDefinedRequest() {
        getLinkPropsResponse = northboundService.getLinkProps(linkPropsRequest.getSrcSwitch(),
                linkPropsRequest.getSrcPort(), linkPropsRequest.getDstSwitch(), linkPropsRequest.getDstPort());
    }

    @And("^delete all link properties$")
    public void deleteAllLinkProperties() {
        List<LinkPropsDto> linkProps = northboundService.getAllLinkProps();
        changePropsResponse = northboundService.deleteLinkProps(linkProps);
    }
}
