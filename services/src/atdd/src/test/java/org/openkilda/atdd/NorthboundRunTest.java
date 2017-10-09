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


import static org.openkilda.flow.FlowUtils.getHealthCheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NorthboundRunTest {
    private static final FlowState expectedFlowStatus = FlowState.UP;
    private static final PathInfoData expectedFlowPath = new PathInfoData(0L, Arrays.asList(
            new PathNode("de:ad:be:ef:00:00:00:03", 2, 0),
            new PathNode("de:ad:be:ef:00:00:00:04", 1, 1),
            new PathNode("de:ad:be:ef:00:00:00:04", 2, 2),
            new PathNode("de:ad:be:ef:00:00:00:05", 1, 3)));

    @Then("^path of flow (.*) could be read$")
    public void checkFlowPath(final String flowId) {
        String flowName = FlowUtils.getFlowName(flowId);

        FlowPathPayload payload = FlowUtils.getFlowPath(flowName);
        assertNotNull(payload);

        assertEquals(flowName, payload.getId());
        assertEquals(expectedFlowPath, payload.getPath());
    }

    @Then("^status of flow (.*) could be read$")
    public void checkFlowStatus(final String flowId) throws Exception {
        String flowName = FlowUtils.getFlowName(flowId);
        FlowIdStatusPayload payload = getFlowState(flowName, expectedFlowStatus);

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
        FlowIdStatusPayload payload = getFlowState(flowName, flowState);
        assertNotNull(payload);
        assertEquals(flowName, payload.getId());
        assertEquals(flowState, payload.getStatus());
    }

    private FlowIdStatusPayload getFlowState(String flowName, FlowState expectedFlowStatus) throws Exception {
        FlowIdStatusPayload payload = FlowUtils.getFlowStatus(flowName);
        for (int i = 0; i < 10; i++) {
            payload = FlowUtils.getFlowStatus(flowName);
            if (payload != null && expectedFlowStatus.equals(payload.getStatus())) {
                break;
            }
            TimeUnit.SECONDS.sleep(2);
        }
        return payload;
    }
}
