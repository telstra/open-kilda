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

import static com.nitorcreations.Matchers.reflectEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.staging.helpers.TopologyUnderTest;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.northbound.dto.switches.DeleteMeterResult;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.model.MeterEntry;
import org.openkilda.testing.service.northbound.NorthboundService;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map.Entry;

@Slf4j
public class NorthboundSteps {

    private FeatureTogglePayload featureToggleRequest;

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    private HealthCheck healthCheckResponse;
    private FeatureTogglePayload featureTogglesResponse;
    private DeleteMeterResult deleteMeterResponse;

    @When("^request Northbound health check$")
    public void requestNorthboundHealthcheck() {
        healthCheckResponse = northboundService.getHealthCheck();
    }

    @Then("^all healthcheck components are (.*)")
    public void allHealthcheckComponentsAreOperational(String componentStatus) {
        assertTrue(healthCheckResponse.getComponents().values().stream().allMatch(c -> c.equals(componentStatus)));
    }

    @When("^get all feature toggles$")
    public void getAllFeatureToggles() {
        featureTogglesResponse = northboundService.getFeatureToggles();
    }

    @When("^create feature toggles request based on the response$")
    public void createFetureTogglesRequestBasedOnTheResponse() {
        featureToggleRequest = new FeatureTogglePayload(featureTogglesResponse);
    }

    @When("^update request: switch each toggle to an opposite state$")
    public void updateRequestSwitchEachToggleToASeparateState() {
        featureToggleRequest.setCreateFlowEnabled(!featureToggleRequest.getCreateFlowEnabled());
        featureToggleRequest.setDeleteFlowEnabled(!featureToggleRequest.getDeleteFlowEnabled());
        featureToggleRequest.setPushFlowEnabled(!featureToggleRequest.getPushFlowEnabled());
        featureToggleRequest.setRerouteOnIslDiscoveryEnabled(
                !featureToggleRequest.getRerouteOnIslDiscoveryEnabled());
        featureToggleRequest.setSyncRulesEnabled(!featureToggleRequest.getSyncRulesEnabled());
        featureToggleRequest.setUnpushFlowEnabled(!featureToggleRequest.getUnpushFlowEnabled());
        featureToggleRequest.setUpdateFlowEnabled(!featureToggleRequest.getUpdateFlowEnabled());
    }

    @When("^send update request to feature toggles$")
    public void sendUpdateRequestToFeatureToggles() {
        featureTogglesResponse = northboundService.toggleFeature(featureToggleRequest);
    }

    @Then("^feature toggles response matches request$")
    public void northboundResponseMatchesRequest() {
        assertThat(featureToggleRequest, reflectEquals(featureTogglesResponse));
    }

    @And("^remove '(.*)' from '(.*)'$")
    public void removeMeter(String meterAlias, String switchAlias) {
        Entry<Integer, MeterEntry> meter = topologyUnderTest.getAliasedObject(meterAlias);
        Switch sw = topologyUnderTest.getAliasedObject(switchAlias);
        deleteMeterResponse = northboundService.deleteMeter(sw.getDpId(), meter.getKey());
    }

    @Then("^remove meter response is successful$")
    public void removeMeterResponseIsSuccessful() {
        assertTrue(deleteMeterResponse.isDeleted());
    }
}
