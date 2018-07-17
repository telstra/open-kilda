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

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.testlib.model.topology.TopologyDefinition;
import org.openkilda.testlib.service.floodlight.FloodlightService;
import org.openkilda.testlib.service.northbound.NorthboundService;

import cucumber.api.java.en.Given;
import cucumber.api.java8.En;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class CleanupSteps implements En {

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private FloodlightService floodlightService;

    @Given("^a clean topology with no flows and no discrepancies in switch rules and meters")
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

        topologyDefinition.getActiveSwitches()
                .forEach(sw -> {
                    try {
                        assertThat(format("Switch %s has unexpected meters installed", sw),
                                floodlightService.getMeters(sw.getDpId()).values(), empty());
                    } catch (UnsupportedOperationException ex) {
                        //TODO: a workaround for not implemented dumpMeters on OF_12 switches.
                        log.warn("Switch {} doesn't support dumping of meters. {}", sw.getDpId(), ex.getMessage());
                    }
                });
    }
}
