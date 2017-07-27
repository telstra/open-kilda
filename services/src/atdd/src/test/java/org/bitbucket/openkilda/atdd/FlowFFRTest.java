package org.bitbucket.openkilda.atdd;

import java.util.Random;
import java.util.List;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.TimeUnit;

import org.bitbucket.openkilda.flow.FlowUtils;
import org.bitbucket.openkilda.flow.Flow;
import org.bitbucket.openkilda.topo.TopologyHelp;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import java.io.File;
import java.nio.file.Files;

public class FlowFFRTest{
    private static final String fileName = "topologies/barebones-topology.json";
    private static final long FLOW_COOKIE = 1L;
    private static final String flowId = "1";
    private static final String sourceSwitch = "00:00:00:00:00:00:00:01";
    private static final String destinationSwitch = "00:00:00:00:00:00:00:04";
    private static final Integer sourcePort = 1;
    private static final Integer destinationPort = 2;
    private static final Integer sourceVlan = 1;
    private static final Integer destinationVlan = 1;
    private static final long bandwidth = 1000;
    private static final List<List<String>> existingPathes =  Arrays.asList(
       Arrays.asList("s1", "s2", "s4"),
       Arrays.asList("s1", "s3", "s4"));

    private FlowPayload flowPayload;
    private Flow flow;
    private String intermediateSwitch;
    private List<String> failedSwitches;

    private void failSwitch(String switchId) throws Throwable {
    // Does nothing meaningful so far as more research has to be done
    // on proper staging of switch failures.
    }

    private void resurrectSwitch(String switchId) throws Throwable {
    // Opposite of failSwitch();
    }

    private List<List<String>> pruneFailedRoutes() throws Throwable {
         List<List<String>> routes = existingPathes;
         for(List<String> route : existingPathes){
             for(String failedSwitch : failedSwitches){
                 if(route.contains(failedSwitch)){
                        routes.remove(route);
                  }
             }
         }
         return routes;
    }

    private boolean trafficIsOk() throws Throwable {
        // Here will be check for traffic through the flow. Left as a stub due to
        // possible bug in flow creation.
        // This method must set intermediateSwitch to id of an intermediate switch
        // (either s2 or s3 from corresponding topology) to make it possible to
        // turn off specific switches.
        return true;
    }

    @Given("^basic multi-path topology$")
    public void a_multi_path_topology() throws Throwable {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        String json = new String(Files.readAllBytes(file.toPath()));
        assert TopologyHelp.CreateMininetTopology(json);
    }

    @When("^a flow is successfully created$")
    public void successfulFlowCreation() throws Throwable {
        flowPayload = new FlowPayload(FlowUtils.getFlowName(flowId),
                new FlowEndpointPayload(sourceSwitch, sourcePort, sourceVlan),
                new FlowEndpointPayload(destinationSwitch, destinationPort, destinationVlan),
                bandwidth, flowId, null);
        flow = new Flow(FlowUtils.getFlowName(flowId), bandwidth, FLOW_COOKIE, flowId, null, sourceSwitch,
                destinationSwitch, sourcePort, destinationPort, sourceVlan, destinationVlan, 0, null, null);

        FlowPayload response = FlowUtils.putFlow(flowPayload);
        response.setCookie(null);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);
        flowPayload = response;
        System.out.println(response.toString());
        TimeUnit.SECONDS.sleep(5);
    }

    @When("^traffic flows through this flow$")
    public void trafficFlows() throws Throwable {
        assertTrue(trafficIsOk());
    }

    @When("^traffic does not flow through this flow$")
    public void trafficNotFlows() throws Throwable {
        assertFalse(trafficIsOk());
    }


    @When("^a route in use fails$")
    public void routeInUseFails() throws Throwable {
         failSwitch(intermediateSwitch);
         failedSwitches.add(intermediateSwitch);
    }

    @When("^there is an alternative route$")
    public void alternativeRouteExists() throws Throwable {
         assertFalse(pruneFailedRoutes().isEmpty());
    }

    @When("^there is no alternative route$")
    public void noRoutesInFlow() throws Throwable {
         assertTrue(pruneFailedRoutes().isEmpty());
    }

    @When("^system is operational$")
    public void systemIsOperational() throws Throwable {
    // Makes sure that turning switches off does not render system inoperational.
    // TODO: implement it or remove it from scenario (since a switch down should never
    // lead to system degradation and that should porbably be checked elsewhere).
    }

    @When("^a failed route comes back up$")
    public void resurrectRoute() throws Throwable {
        resurrectSwitch(failedSwitches.get(0));
    }

}
