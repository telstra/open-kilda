/* Copyright 2017 Telstra Open Source
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

package org.openkilda.atdd;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.openkilda.flow.FlowUtils.getHealthCheck;

import org.openkilda.SwitchesUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NorthboundRunTest {
    private static final FlowState expectedFlowStatus = FlowState.UP;
    private static final List<PathNodePayload> expectedForwardFlowPath = Arrays.asList(
            new PathNodePayload(new SwitchId("de:ad:be:ef:00:00:00:03"), 11, 2),
            new PathNodePayload(new SwitchId("de:ad:be:ef:00:00:00:04"), 1, 2),
            new PathNodePayload(new SwitchId("de:ad:be:ef:00:00:00:05"), 1, 12));
    private static final List<PathNodePayload> expectedReverseFlowPath = Arrays.asList(
            new PathNodePayload(new SwitchId("de:ad:be:ef:00:00:00:05"), 12, 1),
            new PathNodePayload(new SwitchId("de:ad:be:ef:00:00:00:04"), 2, 1),
            new PathNodePayload(new SwitchId("de:ad:be:ef:00:00:00:03"), 2, 11));

    @Then("^path of flow (\\w+) could be read$")
    public void checkFlowPath(final String flowId) {
        String flowName = FlowUtils.getFlowName(flowId);

        FlowPathPayload payload = FlowUtils.getFlowPath(flowName);
        assertNotNull(payload);

        assertEquals(flowName, payload.getId());
        assertEquals(expectedForwardFlowPath, payload.getForwardPath());
        assertEquals(expectedReverseFlowPath, payload.getReversePath());
    }

    @Then("^status of flow (\\w+) could be read$")
    public void checkFlowStatus(final String flowId) throws Exception {
        String flowName = FlowUtils.getFlowName(flowId);
        FlowIdStatusPayload payload = FlowUtils.waitFlowStatus(flowName, expectedFlowStatus);

        assertNotNull(payload);

        assertEquals(flowName, payload.getId());
        assertEquals(expectedFlowStatus, payload.getStatus());
    }

    @Then("^flows dump contains (\\d+) flows$")
    public void checkDumpFlows(final int flowCount) {
        List<FlowPayload> flows = FlowUtils.getFlowDump();
        assertNotNull(flows);
        flows.forEach(flow -> System.out.println(flow.getId()));
        assertEquals(flowCount, flows.size());
    }

    @Given("^health check$")
    public void healthCheck() throws Throwable {
        assertEquals(200, getHealthCheck());
    }

    @Then("^flow (\\w+) in (\\w+) state$")
    public void flowState(String flowId, String state) throws Throwable {
        String flowName = FlowUtils.getFlowName(flowId);
        FlowState flowState = FlowState.valueOf(state);
        FlowIdStatusPayload payload = FlowUtils.waitFlowStatus(flowName, flowState);
        assertNotNull(payload);
        assertEquals(flowName, payload.getId());
        assertEquals(flowState, payload.getStatus());
    }

    @Then("^delete all non-default rules request on ([\\w:]+) switch is successful with (\\d+) rules deleted$")
    public void deleteAllNonDefaultRules(String switchId, int deletedFlowsCount) {
        List<Long> cookies = SwitchesUtils.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS);
        assertNotNull(cookies);
        assertEquals(deletedFlowsCount, cookies.size());
        cookies.forEach(cookie -> System.out.println(cookie));
    }

    @Then("^delete all rules request on (.*) switch is successful with (\\d+) rules deleted$")
    public void deleteAllDefaultRules(String switchId, int deletedFlowsCount) {
        List<Long> cookies = SwitchesUtils.deleteSwitchRules(switchId, DeleteRulesAction.DROP_ALL);
        assertNotNull(cookies);
        assertEquals(deletedFlowsCount, cookies.size());
        cookies.forEach(cookie -> System.out.println(cookie));
    }

    @Then("^delete rules request by (\\d+) in-port on (.*) switch is successful with (\\d+) rules deleted$")
    public void deleteRulesByInPort(int inPort, String switchId, int deletedFlowsCount) {
        List<Long> cookies = SwitchesUtils.deleteSwitchRules(switchId, inPort, null);
        assertNotNull(cookies);
        assertEquals(deletedFlowsCount, cookies.size());
        cookies.forEach(cookie -> System.out.println(cookie));
    }

    @Then("^delete rules request by (\\d+) in-port and (\\d+) "
            + "in-vlan on (.*) switch is successful with (\\d+) rules deleted$")
    public void deleteRulesByInPortVlan(int inPort, int inVlan, String switchId, int deletedFlowsCount) {
        List<Long> cookies = SwitchesUtils.deleteSwitchRules(switchId, inPort, inVlan);
        assertNotNull(cookies);
        assertEquals(deletedFlowsCount, cookies.size());
        cookies.forEach(cookie -> System.out.println(cookie));
    }

    @Then("^delete rules request by (\\d+) in-vlan on (.*) switch is successful with (\\d+) rules deleted$")
    public void deleteRulesByInVlan(int inVlan, String switchId, int deletedFlowsCount) {
        List<Long> cookies = SwitchesUtils.deleteSwitchRules(switchId, null, inVlan);
        assertNotNull(cookies);
        assertEquals(deletedFlowsCount, cookies.size());
        cookies.forEach(cookie -> System.out.println(cookie));
    }

    @Then("^delete rules request by (\\d+) out-port on (.*) switch is successful with (\\d+) rules deleted$")
    public void deleteRulesByOutPort(int outPort, String switchId, int deletedFlowsCount) {
        List<Long> cookies = SwitchesUtils.deleteSwitchRules(switchId, outPort);
        assertNotNull(cookies);
        assertEquals(deletedFlowsCount, cookies.size());
        cookies.forEach(cookie -> System.out.println(cookie));
    }

    @Then("^synchronize flow cache is successful with (\\d+) dropped flows$")
    public void synchronizeFlowCache(final int droppedFlowsCount) {
        FlowCacheSyncResults results = FlowUtils.syncFlowCache();
        assertNotNull(results);
        assertEquals(droppedFlowsCount, results.getDroppedFlows().size());
    }

    @Then("^invalidate flow cache is successful with (\\d+) dropped flows$")
    public void invalidateFlowCache(final int droppedFlowsCount) {
        FlowCacheSyncResults results = FlowUtils.invalidateFlowCache();
        assertNotNull(results);
        assertEquals(droppedFlowsCount, results.getDroppedFlows().size());
    }

    @When("^flow (\\w+) could be deleted from DB$")
    public void deleteFlowFromDbViaTe(final String flowName) {
        String flowId = FlowUtils.getFlowName(flowName);
        boolean deleted = FlowUtils.deleteFlowViaTe(flowId);

        assertTrue(deleted);
    }

    @Then("^validation of rules on ([\\w:]+) switch is successful with no discrepancies$")
    public void validateSwitchRules(String switchId) {
        RulesValidationResult validateResult = SwitchesUtils.validateSwitchRules(switchId);

        assertNotNull(validateResult);
        assertThat("The switch has excessive rules.", validateResult.getExcessRules(), empty());
        assertThat("The switch has missing rules.", validateResult.getMissingRules(), empty());
    }

    @Then("^validation of rules on ([\\w:]+) switch has passed and (\\d+) rules are missing$")
    public void validateSwitchWithMissingRules(String switchId, int missingRulesCount) {
        RulesValidationResult validateResult = SwitchesUtils.validateSwitchRules(switchId);

        assertNotNull(validateResult);
        assertEquals("Switch doesn't have expected missing rules.", missingRulesCount,
                validateResult.getMissingRules().size());
        assertThat("The switch has excessive rules.", validateResult.getExcessRules(), empty());
    }

    @Then("^validation of rules on ([\\w:]+) switch has passed and (\\d+) excessive rules are present$")
    public void validateSwitchWithExcessiveRules(String switchId, int excessiveRulesCount) {
        RulesValidationResult validateResult = SwitchesUtils.validateSwitchRules(switchId);

        assertNotNull(validateResult);
        assertEquals("Switch doesn't have expected excessive rules.", excessiveRulesCount,
                validateResult.getExcessRules().size());
        assertThat("The switch has missing rules.", validateResult.getMissingRules(), empty());
    }

    @Then("^synchronization of rules on ([\\w:]+) switch is successful with (\\d+) rules installed$")
    public void synchronizeSwitchWithInstalledRules(String switchId, int installedRulesCount) {
        RulesSyncResult validateResult = SwitchesUtils.synchronizeSwitchRules(switchId);

        assertNotNull(validateResult);
        assertEquals("Switch doesn't have expected installed rules.", installedRulesCount,
                validateResult.getInstalledRules().size());
    }

    @Then("^([\\w,]+) rules are installed on ([\\w:]+) switch$")
    public void checkThatRulesAreInstalled(String ruleCookies, String switchId) {
        List<FlowEntry> actualRules = SwitchesUtils.dumpSwitchRules(switchId);
        assertNotNull(actualRules);
        List<String> actualRuleCookies = actualRules.stream()
                .map(entry -> Long.toHexString(entry.getCookie()))
                .collect(Collectors.toList());

        assertThat("Switch doesn't contain expected rules.", actualRuleCookies, hasItems(ruleCookies.split(",")));
    }

    @Then("No rules installed on ([\\w:]+) switch$")
    public void checkThatNoRulesAreInstalled(String switchId) {
        List<FlowEntry> actualRules = SwitchesUtils.dumpSwitchRules(switchId);

        assertNotNull(actualRules);
        assertTrue("Switch contains rules, but expect to have none.", actualRules.isEmpty());
    }
}
