/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.atdd;

import org.openkilda.LinksUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.dto.flows.UniFlowVerificationOutput;
import org.openkilda.northbound.dto.flows.VerificationInput;
import org.openkilda.northbound.dto.flows.VerificationOutput;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import org.junit.Assert;

import java.util.HashMap;

public class FlowVerificationTest {
    private final HashMap<String, FlowPayload> ongoingFlows = new HashMap<>();
    private final HashMap<String, VerificationOutput> flowVerificationResults= new HashMap<>();

    @Given("^flow ((?:[0-9a-f]{2})(?::[0-9a-f]{2}){7})\\((\\d+)\\) and ((?:[0-9a-f]{2})(?::[0-9a-f]{2}){7})\\((\\d+)\\) with id=\"([^\"]+)\" is created$")
    public void flowIsCreated(String sourceId, int sourcePort, String destId, int destPort, String flowId) throws Throwable {
        String randomFlowId = FlowUtils.getFlowName(flowId);
        FlowEndpointPayload sourcePoint = new FlowEndpointPayload(sourceId, sourcePort, 96);
        FlowEndpointPayload destPoint = new FlowEndpointPayload(destId, destPort, 112);
        FlowPayload requestPayload = new FlowPayload(
                randomFlowId, sourcePoint, destPoint, 1000, false, "ATDD flow", null,
                FlowState.UP.getState());

        System.out.println(String.format("==> Send flow CREATE request (%s <--> %s)", sourcePoint, destPoint));
        FlowPayload response = FlowUtils.putFlow(requestPayload);
        Assert.assertNotNull(response);
        response.setLastUpdated(null);

        System.out.println(String.format("==> Wait till flow become \"UP\" (%s <--> %s)", sourcePoint, destPoint));
        FlowIdStatusPayload status = FlowUtils.waitFlowStatus(randomFlowId, FlowState.UP);
        Assert.assertNotNull(status);
        Assert.assertEquals(FlowState.UP, status.getStatus());

        ongoingFlows.put(flowId, response);
    }

    @Then("^use flow verification for flow id=\"([^\"]*)\"$")
    public void useFlowVerificationFor(String flowId) throws Throwable {
        FlowPayload flow = ongoingFlows.get(flowId);

        System.out.println(String.format(
                "==> Send flow VERIFY request (%s <--> %s)", flow.getSource(), flow.getDestination()));

        VerificationInput payload = new VerificationInput(4 * 1000);
        VerificationOutput response = FlowUtils.verifyFlow(flow.getId(), payload);
        Assert.assertNotNull("Verification request failed", response);

        flowVerificationResults.put(flowId, response);
    }

    @Then("^(forward|reverse) flow path is broken$")
    public void flowChainIsBroken(String direction) {
        String targetPort = "forward".equals(direction) ? "1" : "2";
        LinksUtils.islFail("00000002", targetPort);
    }

    @Then("^flow verification for flow id=\"([^\"]*)\" is (ok|fail) (ok|fail)$")
    public void flowVerificationIsSuccessful(
            String flowId, String expectForward, String expectReverse) throws Throwable {
        VerificationOutput output = flowVerificationResults.get(flowId);

        dumpVerificationOutput(output);

        UniFlowVerificationOutput forward = output.getForward();
        UniFlowVerificationOutput reverse = output.getReverse();

        Assert.assertEquals(String.format(
                "Flow verification(forward) status don't match expected status (expect: %s, actual: %s, error: %s)",
                expectForward, forward.isPingSuccess() ? "ok" : "fail", forward.getError()),
                "ok".equals(expectForward), forward.isPingSuccess());
        Assert.assertEquals(String.format(
                "Flow verification(reverse) status don't match expected status (expect: %s, actual: %s, error: %s)",
                expectReverse, reverse.isPingSuccess() ? "ok" : "fail", reverse.getError()),
                "ok".equals(expectReverse), reverse.isPingSuccess());
    }

    private void dumpVerificationOutput(VerificationOutput output) {
        String flowId = output.getFlowId();
        UniFlowVerificationOutput forward = output.getForward();
        UniFlowVerificationOutput reverse = output.getReverse();

        System.out.println(String.format("Flow's %s VERIFICATION forward part response - %s", flowId, forward));
        System.out.println(String.format("Flow's %s VERIFICATION reverse part response - %s", flowId, reverse));
    }
}
