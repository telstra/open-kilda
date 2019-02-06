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

import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.service.labservice.LabService;

import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import org.junit.Assume;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

public class CommonSteps {

    // If one of the scenarios fails, with a high probability something is wrong with the test environment and
    // there is no sense to run other scenarios because of a low percentage of their success. So this flag controls
    // other scenario executions after the first scenario failure.
    private static boolean skipScenario = false;
    @Autowired
    private LabService labService;
    @Autowired
    private TopologyDefinition topology;

    @Before
    public void beforeScenario() {
        if (skipScenario) {
            Assume.assumeTrue("The scenario is skipped due to a failure of one of the previous scenarios!", false);
        }
        if (labService.getLab() == null) {
            labService.createHwLab(topology);
        }
    }

    @After
    public void afterScenario(Scenario scenario) {
        if (scenario.isFailed()) {
            skipScenario = true;
        }
    }

    @And("(?:remains? in this state|wait) for (\\d+) seconds")
    public void delay(int seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }
}
