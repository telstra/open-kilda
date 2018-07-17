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

package org.openkilda.atdd.staging.tests.discoverymechanism;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.testlib.service.floodlight.FloodlightService;
import org.openkilda.testlib.service.northbound.NorthboundService;

import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;


@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/discovery_mechanism.feature"},
        glue = {"org.openkilda.atdd.staging.tests.discovery_mechanism", "org.openkilda.atdd.staging.steps"},
        plugin = {"json:target/cucumber-reports/discovery_mechanism_report.json"})
@ActiveProfiles("mock")
public class DiscoveryMechanismTest {

    public static class DiscoveryMechanismHook {

        @Autowired
        private NorthboundService northboundService;

        @Autowired
        private FloodlightService floodlightService;

        @After
        public void verifyMocks() {
            verify(northboundService).getActiveSwitches();
            verify(northboundService).getActiveLinks();

            verify(floodlightService, times(3)).getFlows(any());
        }
    }
}
