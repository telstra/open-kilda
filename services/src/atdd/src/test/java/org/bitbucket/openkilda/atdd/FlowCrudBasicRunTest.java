package org.bitbucket.openkilda.atdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.flow.Flow;
import org.bitbucket.openkilda.flow.FlowUtils;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.util.List;

public class FlowCrudBasicRunTest {
    private static final long FLOW_COOKIE = 1L;
    private FlowPayload flowPayload;
    private int storedFlows;

    @When("^flow (.*) creation request with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is successful$")
    public void successfulFlowCreation(final String flowId, final String sourceSwitch, final int sourcePort,
                                       final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                       final int destinationVlan, final long bandwidth) throws Exception {
        flowPayload = new FlowPayload(FlowUtils.getFlowName(flowId),
                new FlowEndpointPayload(sourceSwitch, sourcePort, sourceVlan),
                new FlowEndpointPayload(destinationSwitch, destinationPort, destinationVlan),
                bandwidth, flowId, null);

        FlowPayload response = FlowUtils.putFlow(flowPayload);
        response.setCookie(null);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);
    }

    @When("^flow (.*) creation request with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is failed$")
    public void failedFlowCreation(final String flowId, final String sourceSwitch, final int sourcePort,
                                   final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                   final int destinationVlan, final long bandwidth) throws Exception {
        flowPayload = new FlowPayload(FlowUtils.getFlowName(flowId),
                new FlowEndpointPayload(sourceSwitch, sourcePort, sourceVlan),
                new FlowEndpointPayload(destinationSwitch, destinationPort, destinationVlan),
                bandwidth, flowId, null);

        FlowPayload response = FlowUtils.putFlow(flowPayload);

        assertNull(response);
    }

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be created$")
    public void checkFlowCreation(final String flowId, final String sourceSwitch, final int sourcePort,
                                  final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                  final int destinationVlan, final int bandwidth) throws Exception {
        Flow expectedFlow = new Flow(FlowUtils.getFlowName(flowId), bandwidth, FLOW_COOKIE, flowId, null, sourceSwitch,
                destinationSwitch, sourcePort, destinationPort, sourceVlan, destinationVlan, 0, null, null);

        List<Flow> flows = validateFlowStored();

        assertFalse(flows.isEmpty());

        storedFlows = flows.size();

        assertTrue(flows.contains(expectedFlow));
    }

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be read$")
    public void checkFlowRead(final String flowId, final String sourceSwitch, final int sourcePort,
                              final int sourceVlan, final String destinationSwitch, final int destinationPort,
                              final int destinationVlan, final int bandwidth) throws Exception {
        FlowPayload flow = FlowUtils.getFlow(FlowUtils.getFlowName(flowId));
        assertNotNull(flow);

        System.out.println(String.format("===> Flow was created at %s\n", flow.getLastUpdated()));

        assertEquals(FlowUtils.getFlowName(flowId), flow.getId());
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

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be updated with (\\d+)$")
    public void checkFlowUpdate(final String flowId, final String sourceSwitch, final int sourcePort,
                                final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                final int destinationVlan, final int band, final int newBand) throws Exception {
        flowPayload.setMaximumBandwidth((long) newBand);

        FlowPayload response = FlowUtils.updateFlow(FlowUtils.getFlowName(flowId), flowPayload);
        response.setCookie(null);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);

        checkFlowCreation(flowId, sourceSwitch, sourcePort, sourceVlan, destinationSwitch,
                destinationPort, destinationVlan, newBand);
    }

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be deleted$")
    public void checkFlowDeletion(final String flowId, final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                  final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                  final int bandwidth) throws Exception {
        FlowPayload response = FlowUtils.deleteFlow(FlowUtils.getFlowName(flowId));
        response.setCookie(null);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);

        FlowPayload flow = FlowUtils.getFlow(FlowUtils.getFlowName(flowId));

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

    @Then("^flows count is (\\d+)$")
    public void checkFlowCount(final int expectedFlowsCount) throws Exception {
        List<Flow> flows = validateFlowStored();
        // one reverse flow and one forward flow for every created flow
        assertEquals(expectedFlowsCount * 2, flows.size());
    }

    private List<Flow> validateFlowStored() throws Exception {
        List<Flow> flows = FlowUtils.dumpFlows();
        System.out.print(String.format("===> Flows retrieved: %d\n", flows.size()));
        return flows;
    }
}
