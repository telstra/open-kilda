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
import static org.junit.Assert.assertThat;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Isl;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.steps.helpers.TopologyUnderTest;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.links.LinkPropsDto;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
public class LinkPropertiesSteps {

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    private LinkPropsDto linkPropsRequest;
    private BatchResults changePropsResponse;
    private List<LinkPropsDto> getLinkPropsResponse;


    @When("^create link properties request for ISL '(.*)'$")
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
        assertThat(value, equalTo(String.valueOf(props.getProperty(key))));
    }

    @When("^send delete link properties request$")
    public void sendDeleteLinkPropertiesRequest() {
        changePropsResponse = northboundService.deleteLinkProps(singletonList(linkPropsRequest));
    }

    @Then("^link props response has (\\d+) results?$")
    public void responseHasResults(int resultsAmount) {
        assertEquals(resultsAmount, getLinkPropsResponse.size());
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

    @When("^set (.*) of '(.*)' ISL to (\\d+)$")
    public void setCostOfIsl(String propName, String islAlias, int newCost) {
        createLinkPropertiesRequest(islAlias);
        linkPropsRequest.setProperty(propName, String.valueOf(newCost));
        changePropsResponse = northboundService.updateLinkProps(Collections.singletonList(linkPropsRequest));
    }

    @Then("^property '(.*)' of (.*) ISL equals to '(.*)'$")
    public void verifyIslProp(String propName, String islAlias, String expectedValue) {
        createLinkPropertiesRequest(islAlias);
        getLinkPropertiesForDefinedRequest();
        assertEquals(expectedValue, getLinkPropsResponse.get(0).getProperty(propName));
    }
}
