package org.openkilda.atdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.openkilda.LinksUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.junit.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowIgnoreBandwidthTest {
    private final Map<String, String> createdFlows;

    public FlowIgnoreBandwidthTest() {
        createdFlows = new HashMap<>();
    }

    @Then("^available ISL's bandwidths between ([0-9a-f]{2}(?::[0-9a-f]{2}){7}) and ([0-9a-f]{2}(?::[0-9a-f]{2}){7}) is (\\d+)$")
    public void available_ISL_bandwidths_between_switches(String source, String dest, long expected) throws Throwable {
        List<IslInfoData> islLinks = LinksUtils.dumpLinks();

        Long actual = null;
        for (IslInfoData link : islLinks) {
            if (link.getPath().size() != 2) {
                throw new RuntimeException(
                        String.format("ISL's link path contain %d records, expect 2", link.getPath().size()));
            }
            PathNode left = link.getPath().get(0);
            PathNode right = link.getPath().get(1);

            if (! source.equals(left.getSwitchId())) {
                continue;
            }

            if (! dest.equals(right.getSwitchId())) {
                continue;
            }

            actual = link.getAvailableBandwidth();
            break;
        }

        Assert.assertNotNull(actual);
        Assert.assertEquals("Actual bandwidth does not match expectations.", expected, (long)actual);
        System.out.println(String.format("Available bandwidth between %s and %s is %d", source, dest, actual));
    }

    @When("^flow ignore bandwidth between ([0-9a-f]{2}(?::[0-9a-f]{2}){7}) and ([0-9a-f]{2}(?::[0-9a-f]{2}){7}) with (\\d+) bandwidth is created$")
    public void flow_ignore_bandwidth_between_switches_with_bandwidth_is_created(String source, String dest, int bandwidth) throws Throwable {
        String flowId = FlowUtils.getFlowName("flowId");
        FlowEndpointPayload sourcePoint = new FlowEndpointPayload(source, 1, 0);
        FlowEndpointPayload destPoint = new FlowEndpointPayload(dest, 2, 0);
        FlowPayload requestPayload = new FlowPayload(
                flowId, sourcePoint, destPoint, bandwidth, true, "Flow that ignore ISL bandwidth", null);

        System.out.println(String.format("==> Send flow CREATE request (%s <--> %s)", source, dest));
        FlowPayload response = FlowUtils.putFlow(requestPayload);
        Assert.assertNotNull(response);
        response.setLastUpdated(null);

        System.out.println(String.format("==> Wait till flow become \"UP\" (%s <--> %s)", source, dest));
        FlowIdStatusPayload status = FlowUtils.waitFlowStatus(flowId, FlowState.UP);
        assertNotNull(status);
        assertEquals(FlowState.UP, status.getStatus());

        saveCreatedFlowId(source, dest, flowId);
    }

    @When("^drop created flow between ([0-9a-f]{2}(?::[0-9a-f]{2}){7}) and ([0-9a-f]{2}(?::[0-9a-f]{2}){7})$")
    public void drop_created_early_flow(String source, String dest) throws Exception {
        String flowId = lookupCreatedFlowId(source, dest);

        System.out.println(String.format("==> Send flow DELETE request (%s <--> %s)", source, dest));
        FlowPayload response = FlowUtils.deleteFlow(flowId);
        assertNotNull(response);

        System.out.println(String.format("==> Wait till flow become \"DOWN\" (%s <--> %s)", source, dest));
        FlowUtils.waitFlowDeletion(flowId);
    }

    private void saveCreatedFlowId(String source, String dest, String flowId) {
        createdFlows.put(makeCreatedFlowIdKey(source, dest), flowId);
    }

    private String lookupCreatedFlowId(String source, String dest) throws IllegalAccessException {
        String key = makeCreatedFlowIdKey(source, dest);
        if (! createdFlows.containsKey(key)) {
            throw new IllegalAccessException(String.format("There is no known flows between %s and %s", source, dest));
        }
        return createdFlows.get(key);
    }

    private String makeCreatedFlowIdKey(String source, String dest) {
        return String.join("<-->", source, dest);
    }
}
