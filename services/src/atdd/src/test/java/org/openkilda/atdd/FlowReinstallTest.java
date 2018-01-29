package org.openkilda.atdd;

import static org.junit.Assert.assertTrue;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.openkilda.SwitchesUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.payload.flow.FlowPathPayload;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class FlowReinstallTest {

    @When("^switch (.*) is turned off$")
    public void turnOffSwitch(String switchName) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        assertTrue("Switch should be turned off", SwitchesUtils.knockoutSwitch(switchName));
    }

    @When("^switch (.*) is turned on")
    public void turnOnSwitch(String switchName) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        assertTrue("Switch should be turned on", SwitchesUtils.reviveSwitch(switchName));
    }

    @Then("^flow (.*) is built through (.*) switch")
    public void flowPathContainsSwitch(String flowId, String switchId) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        FlowPathPayload payload = FlowUtils.getFlowPath(FlowUtils.getFlowName(flowId));
        assertTrue("Flow path should exist", payload != null && payload.getPath() != null);
        List<PathNode> path = payload.getPath().getPath();
        assertTrue(path.stream()
                .anyMatch(node -> switchId.equalsIgnoreCase(node.getSwitchId())));
    }

    @Then("^flow (.*) is not built through (.*) switch")
    public void flowPathDoesntContainSwitch(String flowId, String switchId) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        FlowPathPayload payload = FlowUtils.getFlowPath(FlowUtils.getFlowName(flowId));
        assertTrue("Flow path should exist", payload != null && payload.getPath() != null);
        List<PathNode> path = payload.getPath().getPath();
        assertTrue(path.stream()
                .noneMatch(node -> switchId.equalsIgnoreCase(node.getSwitchId())));
    }
}
