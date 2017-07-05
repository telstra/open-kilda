package org.bitbucket.openkilda.atdd;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.bitbucket.openkilda.flow.FlowUtils;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowStatusType;
import org.bitbucket.openkilda.messaging.payload.flow.FlowsPayload;

import cucumber.api.java.en.Then;

import java.util.Arrays;
import java.util.List;

public class NorthboundRunTest {
    private static final FlowStatusType expectedFlowStatus = FlowStatusType.UP;
    private static final List<String> expectedFlowPath =
            Arrays.asList("de:ad:be:ef:00:00:00:02", "de:ad:be:ef:00:00:00:03", "de:ad:be:ef:00:00:00:04");

    @Then("^path of flow (.*) could be read$")
    public void checkFlowPath(final String flowId) {
        String flowName = FlowUtils.getFlowName(flowId);

        FlowPathPayload payload = FlowUtils.getFlowPath(flowName);
        assertNotNull(payload);

        assertEquals(flowName, payload.getId());
        assertEquals(expectedFlowPath, payload.getPath());
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
        FlowsPayload payload = FlowUtils.getFlowDump();
        assertNotNull(payload);

        List<FlowPayload> flows = payload.getFlowList();
        assertNotNull(flows);

        assertEquals(flowCount, flows.size());
    }
}
