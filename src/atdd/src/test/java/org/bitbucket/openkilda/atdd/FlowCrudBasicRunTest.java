package org.bitbucket.openkilda.atdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.flow.Flow;
import org.bitbucket.openkilda.flow.FlowUtils;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class FlowCrudBasicRunTest {
    private static final long FLOW_COOKIE = 1L;
    private static final String BASE_FLOW_NAME = "atdd-flow-";
    private FlowPayload flowPayload;
    private String flowName;
    private int storedFlows;

    @When("^flow creation request with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is successful")
    public void createFlow(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                           final String destinationSwitch, final int destinationPort, final int destinationVlan,
                           final long bandwidth) throws Throwable {
        flowName = BASE_FLOW_NAME + UUID.randomUUID().toString();
        flowPayload = new FlowPayload(flowName,
                new FlowEndpointPayload(sourceSwitch, sourcePort, sourceVlan),
                new FlowEndpointPayload(destinationSwitch, destinationPort, destinationVlan),
                bandwidth, BASE_FLOW_NAME, null);
        FlowPayload response = FlowUtils.putFlow(flowPayload);
        assertEquals(flowPayload, response);
    }

    @Then("^flow with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be created$")
    public void checkFlowCreation(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                  final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                  final int bandwidth) throws Throwable {
        Flow expectedFlow = new Flow(flowName, bandwidth, FLOW_COOKIE, BASE_FLOW_NAME, null, sourceSwitch,
                destinationSwitch, sourcePort, destinationPort, sourceVlan, destinationVlan, 0, null, null);
        List<Flow> flows = validateFlowStored();
        assertFalse(flows.isEmpty());
        storedFlows = flows.size();
        assertTrue(flows.contains(expectedFlow));
    }

    @Then("^flow with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be read$")
    public void checkFlowRead(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                              final String destinationSwitch, final int destinationPort, final int destinationVlan,
                              final int bandwidth) throws Throwable {
        FlowPayload flow = FlowUtils.getFlow(flowName);
        assertNotNull(flow);
        System.out.println(String.format("===> Flow was created at %s\n", flow.getLastUpdated()));
        assertEquals(flowName, flow.getId());
        assertEquals(sourceSwitch, flow.getSource().getSwitchId());
        assertEquals(sourcePort, flow.getSource().getPortId().longValue());
        assertEquals(sourceVlan, flow.getSource().getVlanId().longValue());
        assertEquals(destinationSwitch, flow.getDestination().getSwitchId());
        assertEquals(destinationPort, flow.getDestination().getPortId().longValue());
        assertEquals(destinationVlan, flow.getDestination().getVlanId().longValue());
        assertEquals(bandwidth, flow.getMaximumBandwidth().longValue());
        assertNotNull(flow.getLastUpdated());
        assertNotNull(flow.getCookie());
    }

    @Then("^flow with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be updated with (\\d+)$")
    public void checkFlowUpdate(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                final int bandwidth, final int newBandwidth) throws Throwable {
        flowPayload.setMaximumBandwidth((long) newBandwidth);
        FlowPayload response = FlowUtils.updateFlow(flowName, flowPayload);
        assertEquals(flowPayload, response);
        checkFlowCreation(sourceSwitch, sourcePort, sourceVlan, destinationSwitch,
                destinationPort, destinationVlan, newBandwidth);
    }

    @Then("^flow with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be deleted$")
    public void checkFlowDeletion(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                  final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                  final int bandwidth) throws Throwable {
        FlowIdStatusPayload flowId = FlowUtils.deleteFlow(flowName);
        assertEquals(flowName, flowId.getId());
        FlowPayload flow = FlowUtils.getFlow(flowName);
        assertNull(flow);

        List<Flow> flows = validateFlowStored();
        assertEquals(storedFlows - 2, flows.size());
    }

    @Then("^rules with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) are installed$")
    public void checkRulesInstall(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                  final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                  final int bandwidth) throws Throwable {
        // TODO: implement
    }

    @Then("^rules with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) are updated with (\\d+)$")
    public void checkRulesUpdate(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                 final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                 final int bandwidth, final int newBandwidth) throws Throwable {
        // TODO: implement
    }

    @Then("^rules with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) are deleted$")
    public void checkRulesDeletion(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                   final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                   final int bandwidth) throws Throwable {
        // TODO: implement
    }

    @Then("^traffic through (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is forwarded$")
    public void checkTrafficIsForwarded(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                        final String destinationSwitch, final int destinationPort,
                                        final int destinationVlan, final int bandwidth) throws Throwable {
        // TODO: implement
    }

    @Then("^traffic through (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is not forwarded$")
    public void checkTrafficIsNotForwarded(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                           final String destinationSwitch, final int destinationPort,
                                           final int destinationVlan, final int bandwidth) throws Throwable {
        // TODO: implement
    }

    private List<Flow> validateFlowStored() throws Exception {
        for (int i = 5; i > 0; i++) {
            Thread.sleep(100);
            List<Flow> flows = FlowUtils.dumpFlows();
            System.out.print(String.format("===> Flows retrieved: %d\n", flows.size()));
            if (flows.isEmpty() && flows.size() % 2 == 1) {
                Thread.sleep(1000);
            } else {
                return flows;
            }
        }
        return Collections.emptyList();
    }
}
