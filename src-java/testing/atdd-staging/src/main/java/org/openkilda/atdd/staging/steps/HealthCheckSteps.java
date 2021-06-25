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

import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.testing.service.northbound.NorthboundService;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
public class HealthCheckSteps {

    @Autowired @Qualifier("northboundServiceImpl")
    private NorthboundService northboundService;

    private HealthCheck healthCheckResponse;

    @When("^request Northbound health check$")
    public void requestNorthboundHealthcheck() {
        healthCheckResponse = northboundService.getHealthCheck();
    }

    @Then("^all health check components are (.*)")
    public void allHealthCheckComponentsAreOperational(String componentStatus) {
        assertTrue(healthCheckResponse.getComponents().values().stream().allMatch(c -> c.equals(componentStatus)));
    }
}
