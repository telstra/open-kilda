package org.bitbucket.openkilda.atdd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.flow.FlowUtils;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.util.List;

import org.glassfish.jersey.client.ClientConfig;
import static org.bitbucket.openkilda.DefaultParameters.trafficEndpoint;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;
import org.bitbucket.openkilda.topo.TopologyHelp;
import java.io.File;
import java.nio.file.Files;

public class FlowCrudBasicRunTest {
    private static final long FLOW_COOKIE = 1L;
    private FlowPayload flowPayload;
    private int storedFlows;
    private static final String fileName = "topologies/nonrandom-topology.json";

    @Given("^a nonrandom linear topology of 5 switches$")
    public void a_multi_path_topology() throws Throwable {
         ClassLoader classLoader = getClass().getClassLoader();
         File file = new File(classLoader.getResource(fileName).getFile());
         String json = new String(Files.readAllBytes(file.toPath()));
         assert TopologyHelp.CreateMininetTopology(json);
    }

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
                destinationSwitch, sourcePort, destinationPort, sourceVlan, destinationVlan, 0, 0, null, FlowState.UP);

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
        TimeUnit.SECONDS.sleep(2);
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
           .target(trafficEndpoint)
           .path("/checkflowtraffic")
           .queryParam("srcswitch", "00000001")
           .queryParam("dstswitch", "00000005")
           .queryParam("srcport", 1)
           .queryParam("dstport", 1)
           .queryParam("srcvlan", sourceVlan)
           .queryParam("dstvlan", destinationVlan)
           .request()
           .get();
         System.out.println("STATUS =" + result.getStatus());
        assert result.getStatus() == 200;
    }

    @Then("^traffic through (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is not forwarded$")
    public void checkTrafficIsNotForwarded(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                           final String destinationSwitch, final int destinationPort,
                                           final int destinationVlan, final int bandwidth) throws Throwable {
        TimeUnit.SECONDS.sleep(2);
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
           .target(trafficEndpoint)
           .path("/checkflowtraffic")
           .queryParam("srcswitch", "00000001")
           .queryParam("dstswitch", "00000005")
           .queryParam("srcport", 1)
           .queryParam("dstport", 1)
           .queryParam("srcvlan", sourceVlan)
           .queryParam("dstvlan", destinationVlan)
           .request()
           .get();
         System.out.println("STATUS =" + result.getStatus());
        assert result.getStatus() != 200;
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
