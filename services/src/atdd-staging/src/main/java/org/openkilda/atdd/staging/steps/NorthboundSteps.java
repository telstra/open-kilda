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

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.steps.helpers.TopologyUnderTest;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.payload.FeatureTogglePayload;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class NorthboundSteps {

    private static final Logger LOGGER = LoggerFactory.getLogger(NorthboundSteps.class);

    FeatureTogglePayload featureToggleRequest;

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    @When("^request Northbound health check$")
    public void requestNorthboundHealthcheck() {
        topologyUnderTest.setResponse(northboundService.getHealthCheck());
    }

    @Then("^all healthcheck components are (.*)")
    public void allHealthcheckComponentsAreOperational(String componentStatus) {
        HealthCheck response = (HealthCheck) topologyUnderTest.getResponse();
        assertTrue(response.getComponents().values().stream().allMatch(c -> c.equals(componentStatus)));
    }

    @When("^get all feature toggles$")
    public void getAllFeatureToggles() {
        topologyUnderTest.setResponse(northboundService.getFeatureToggles());
    }

    @When("^create feature toggles request based on the response$")
    public void createFetureTogglesRequestBasedOnTheResponse() {
        featureToggleRequest = new FeatureTogglePayload((FeatureTogglePayload) topologyUnderTest.getResponse());
    }

    @When("^update request: switch each toggle to a separate state$")
    public void updateRequestSwitchEachToggleToASeparateState() {
        featureToggleRequest.setCreateFlowEnabled(!featureToggleRequest.getCreateFlowEnabled());
        featureToggleRequest.setDeleteFlowEnabled(!featureToggleRequest.getDeleteFlowEnabled());
        featureToggleRequest.setPushFlowEnabled(!featureToggleRequest.getPushFlowEnabled());
        featureToggleRequest.setReflowOnSwitchActivationEnabled(
                !featureToggleRequest.getReflowOnSwitchActivationEnabled());
        featureToggleRequest.setSyncRulesEnabled(!featureToggleRequest.getSyncRulesEnabled());
        featureToggleRequest.setUnpushFlowEnabled(!featureToggleRequest.getUnpushFlowEnabled());
        featureToggleRequest.setUpdateFlowEnabled(!featureToggleRequest.getUpdateFlowEnabled());
    }

    @When("^send update request to feature toggles$")
    public void sendUpdateRequestToFeatureToggles() {
        topologyUnderTest.setResponse(northboundService.toggleFeature(featureToggleRequest));
    }

    @Then("^feature toggles response matches request$")
    public void northboundResponseMatchesRequest() {
        assertThat(featureToggleRequest, reflectEquals(topologyUnderTest.getResponse()));
    }
}
