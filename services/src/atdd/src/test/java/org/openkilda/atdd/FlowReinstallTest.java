package org.openkilda.atdd;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.SwitchesUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.PathNodePayload;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.apache.commons.lang.StringUtils;
import org.awaitility.Duration;

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

    @Then("^flow (.*) is(.*) built through (.*) switch")
    public void flowPathContainsSwitch(final String flow, final String shouldNotContain, final String switchId)
            throws InterruptedException {
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(Duration.ONE_SECOND)
                .until(() -> {
            FlowPathPayload payload = FlowUtils.getFlowPath(FlowUtils.getFlowName(flow));
            assertTrue("Flow path should exist", payload != null && payload.getForwardPath() != null);
            List<PathNodePayload> path = payload.getForwardPath();
            boolean contains = path.stream()
                    .anyMatch(node -> switchId.equalsIgnoreCase(node.getSwitchId()));

            if (StringUtils.isBlank(shouldNotContain)) {
                return contains;
            } else {
                return !contains;
            }
        });
    }

    @When("flow reroute feature is (on|off)$")
    public void flowRerouteFeatureStatus(final String statusString) {
        boolean status = statusString.equals("on");

        FeatureTogglePayload desired = new FeatureTogglePayload(null, status, null, null, null, null, null);
        FeatureTogglePayload result = FlowUtils.updateFeaturesStatus(desired);

        assertNotNull(result);
        assertEquals(status, result.getReflowOnSwitchActivationEnabled());
        assertEquals(desired.getReflowOnSwitchActivationEnabled(), result.getReflowOnSwitchActivationEnabled());
    }
}
