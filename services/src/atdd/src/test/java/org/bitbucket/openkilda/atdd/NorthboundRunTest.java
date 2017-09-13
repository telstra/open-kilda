package org.bitbucket.openkilda.atdd;


import static org.bitbucket.openkilda.flow.FlowUtils.getHealthCheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.flow.FlowUtils;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

import java.util.Arrays;
import java.util.List;

public class NorthboundRunTest {
    private static final FlowState expectedFlowStatus = FlowState.UP;
    private static final PathInfoData expectedFlowPath1 = new PathInfoData(0L, Arrays.asList(
            new PathNode("de:ad:be:ef:00:00:00:03", 4, 0),
            new PathNode("de:ad:be:ef:00:00:00:04", 4, 1),
            new PathNode("de:ad:be:ef:00:00:00:04", 3, 2),
            new PathNode("de:ad:be:ef:00:00:00:05", 2, 3)));
    private static final PathInfoData expectedFlowPath2 = new PathInfoData(0L, Arrays.asList(
            new PathNode("de:ad:be:ef:00:00:00:03", 3, 0),
            new PathNode("de:ad:be:ef:00:00:00:04", 1, 1),
            new PathNode("de:ad:be:ef:00:00:00:04", 3, 2),
            new PathNode("de:ad:be:ef:00:00:00:05", 2, 3)));
    private static final PathInfoData expectedFlowPath3 = new PathInfoData(0L, Arrays.asList(
            new PathNode("de:ad:be:ef:00:00:00:03", 4, 0),
            new PathNode("de:ad:be:ef:00:00:00:04", 4, 1),
            new PathNode("de:ad:be:ef:00:00:00:04", 2, 2),
            new PathNode("de:ad:be:ef:00:00:00:05", 3, 3)));
    private static final PathInfoData expectedFlowPath4 = new PathInfoData(0L, Arrays.asList(
            new PathNode("de:ad:be:ef:00:00:00:03", 3, 0),
            new PathNode("de:ad:be:ef:00:00:00:04", 1, 1),
            new PathNode("de:ad:be:ef:00:00:00:04", 2, 2),
            new PathNode("de:ad:be:ef:00:00:00:05", 3, 3)));
    private static final List<PathInfoData> expectedAlternativePaths = Arrays.asList(
            expectedFlowPath1, expectedFlowPath2, expectedFlowPath3, expectedFlowPath4);

    @Then("^path of flow (.*) could be read$")
    public void checkFlowPath(final String flowId) {
        String flowName = FlowUtils.getFlowName(flowId);

        FlowPathPayload payload = FlowUtils.getFlowPath(flowName);
        assertNotNull(payload);

        assertEquals(flowName, payload.getId());
        assertTrue(expectedAlternativePaths.contains(payload.getPath()));
    }

    @Then("^status of flow (.*) could be read$")
    public void checkFlowStatus(final String flowId) {
        String flowName = FlowUtils.getFlowName(flowId);

        FlowIdStatusPayload payload = FlowUtils.getFlowStatus(flowName);
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
}
