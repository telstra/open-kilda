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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cucumber.api.java.en.Given;
import cucumber.api.java8.En;
import net.jodah.failsafe.RetryPolicy;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CleanupSteps implements En {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupSteps.class);

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private RetryPolicy retryPolicy;

    @Given("^a clean topology with no flows and no discrepancies switch rules")
    public void cleanupFlowsAndSwitches() {
        northboundService.deleteAllFlows();
        assertTrue(northboundService.getAllFlows().isEmpty());

        topologyDefinition.getActiveSwitches().stream()
                .peek(sw -> northboundService.deleteSwitchRules(sw.getDpId()))
                .map(sw -> northboundService.synchronizeSwitchRules(sw.getDpId()))
                .forEach(rulesSyncResult -> {
                    assertThat(rulesSyncResult.getExcessRules(), empty());
                    assertEquals(0,
                            rulesSyncResult.getMissingRules().size() - rulesSyncResult.getInstalledRules().size());
                });
    }
}
