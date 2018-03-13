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

import static org.openkilda.DefaultParameters.trafficEndpoint;
import static org.openkilda.DefaultParameters.mininetEndpoint;
import static org.openkilda.flow.FlowUtils.getTimeDuration;
import static org.openkilda.flow.FlowUtils.isTrafficTestsEnabled;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.topo.TopologyHelp;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.glassfish.jersey.client.ClientConfig;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

public class FlowCrudBasicRunTest {
    private FlowPayload flowPayload;
    private static final String fileName = "topologies/nonrandom-topology.json";

    @Given("^a nonrandom linear topology of 7 switches$")
    public void a_multi_path_topology() throws Throwable {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        String json = new String(Files.readAllBytes(file.toPath()));
        assertTrue(TopologyHelp.CreateMininetTopology(json));
        // Should also wait for some of this to come up

    }

    @When("^flow (.*) creation request with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is successful$")
    public void successfulFlowCreation(final String flowId, final String sourceSwitch, final int sourcePort,
                                       final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                       final int destinationVlan, final int bandwidth) throws Exception {
        flowPayload = new FlowPayload(FlowUtils.getFlowName(flowId),
                new FlowEndpointPayload(sourceSwitch, sourcePort, sourceVlan),
                new FlowEndpointPayload(destinationSwitch, destinationPort, destinationVlan),
                bandwidth, false, flowId, null);

        FlowPayload response = FlowUtils.putFlow(flowPayload);
        assertNotNull(response);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);
    }

    @When("^flow (.*) creation request with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is failed$")
    public void failedFlowCreation(final String flowId, final String sourceSwitch, final int sourcePort,
                                   final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                   final int destinationVlan, final int bandwidth) throws Exception {
        flowPayload = new FlowPayload(FlowUtils.getFlowName(flowId),
                new FlowEndpointPayload(sourceSwitch, sourcePort, sourceVlan),
                new FlowEndpointPayload(destinationSwitch, destinationPort, destinationVlan),
                bandwidth, false, flowId, null);

        FlowPayload response = FlowUtils.putFlow(flowPayload);

        assertNull(response);
    }

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be created$")
    public void checkFlowCreation(final String flowId, final String sourceSwitch, final int sourcePort,
                                  final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                  final int destinationVlan, final int bandwidth) throws Exception {
        Flow expectedFlow = new Flow(FlowUtils.getFlowName(flowId), bandwidth, false, 0, flowId, null, sourceSwitch,
                destinationSwitch, sourcePort, destinationPort, sourceVlan, destinationVlan, 0, 0, null, null);

        List<Flow> flows = validateFlowStored(expectedFlow);

        assertFalse(flows.isEmpty());
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
        assertEquals(sourceSwitch, flow.getSource().getSwitchDpId());
        assertEquals(sourcePort, flow.getSource().getPortId().longValue());
        assertEquals(sourceVlan, flow.getSource().getVlanId().longValue());
        assertEquals(destinationSwitch, flow.getDestination().getSwitchDpId());
        assertEquals(destinationPort, flow.getDestination().getPortId().longValue());
        assertEquals(destinationVlan, flow.getDestination().getVlanId().longValue());
        assertEquals(bandwidth, flow.getMaximumBandwidth());
        assertNotNull(flow.getLastUpdated());
    }

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be updated with (\\d+)$")
    public void checkFlowUpdate(final String flowId, final String sourceSwitch, final int sourcePort,
                                final int sourceVlan, final String destinationSwitch, final int destinationPort,
                                final int destinationVlan, final int band, final int newBand) throws Exception {
        flowPayload.setMaximumBandwidth(newBand);

        FlowPayload response = FlowUtils.updateFlow(FlowUtils.getFlowName(flowId), flowPayload);
        assertNotNull(response);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);

        checkFlowCreation(flowId, sourceSwitch, sourcePort, sourceVlan, destinationSwitch,
                destinationPort, destinationVlan, newBand);
    }

    @Then("^flow (.*) with (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) could be deleted$")
    public void checkFlowDeletion(final String flowId, final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                  final String destinationSwitch, final int destinationPort, final int destinationVlan,
                                  final int bandwidth) throws Exception {
        int unknownFlowCount = -1; // use -1 to communicate "I don't know what it should be")
        int expectedFlowCount = getFlowCount(unknownFlowCount) - 2;

        FlowPayload response = FlowUtils.deleteFlow(FlowUtils.getFlowName(flowId));
        assertNotNull(response);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);

        FlowPayload flow = FlowUtils.getFlow(FlowUtils.getFlowName(flowId));
        if (flow != null) {
            TimeUnit.SECONDS.sleep(2);
            flow = FlowUtils.getFlow(FlowUtils.getFlowName(flowId));
        }

        assertNull(flow);

        int actualFlowCount = getFlowCount(expectedFlowCount);
        assertEquals(expectedFlowCount, actualFlowCount);
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

    private int checkTraffic(String sourceSwitch, String destSwitch, int sourceVlan, int destinationVlan, int expectedStatus) {
        if (isTrafficTestsEnabled()) {
            System.out.print("=====> Send traffic");

            long current = System.currentTimeMillis();
            Client client = ClientBuilder.newClient(new ClientConfig());

            // NOTE: current implementation requires two auxiliary switches in
            // topology, one for generating traffic, another for receiving it.
            // Originaly smallest meaningful topology was supposed to consist
            // of three switches, thus switches 1 and 5 were used as auxiliary.
            // Now some scenarios require even smaller topologies and at the same
            // time they reuse small linear topology. This leads to shutting off of
            // switches 1 and 5 from flows and to test failures. Since topology
            // reuse speeds testing up the code below determines which switch:port
            // pairs should be used as source and drains for traffic while keepig
            // small linear topology in use.
            int fromNum = Integer.parseInt(sourceSwitch.substring(sourceSwitch.length() -1 ));
            int toNum = Integer.parseInt(destSwitch.substring(destSwitch.length() -1 ));
            String from = "0000000" + (fromNum - 1);
            String to = "0000000" + (toNum + 1);
            int fromPort = from.equals("00000001") ? 1 : 2;
            int toPort = 1;
            System.out.println(String.format("from:%s:%d::%d via %s, To:%s:%d::%d via %s",
                    from,fromPort,sourceVlan,sourceSwitch,
                    to,toPort,destinationVlan,destSwitch));


            Response result = client
                    .target(trafficEndpoint)
                    .path("/checkflowtraffic")
                    .queryParam("srcswitch", from)
                    .queryParam("dstswitch", to)
                    .queryParam("srcport", fromPort)
                    .queryParam("dstport", toPort)
                    .queryParam("srcvlan", sourceVlan)
                    .queryParam("dstvlan", destinationVlan)
                    .request()
                    .get();

            System.out.println(String.format("======> Response = %s", result.toString()));
            System.out.println(String.format("======> Send traffic Time: %,.3f", getTimeDuration(current)));

            return result.getStatus();
        } else {
            return expectedStatus;
        }
    }


    private int checkPingTraffic(String sourceSwitch, String destSwitch,
                                 int sourceVlan, int destinationVlan, int expectedStatus) {
        if (isTrafficTestsEnabled()) {
            System.out.print("=====> Send PING traffic");

            long current = System.currentTimeMillis();
            Client client = ClientBuilder.newClient(new ClientConfig());

            // NOTE: current implementation requires two auxiliary switches in
            // topology, one for generating traffic, another for receiving it.
            // Originaly smallest meaningful topology was supposed to consist
            // of three switches, thus switches 1 and 5 were used as auxiliary.
            // Now some scenarios require even smaller topologies and at the same
            // time they reuse small linear topology. This leads to shutting off of
            // switches 1 and 5 from flows and to test failures. Since topology
            // reuse speeds testing up the code below determines which switch:port
            // pairs should be used as source and drains for traffic while keepig
            // small linear topology in use.
            int fromNum = Integer.parseInt(sourceSwitch.substring(sourceSwitch.length() -1 ));
            int toNum = Integer.parseInt(destSwitch.substring(destSwitch.length() -1 ));
            String from = "0000000" + (fromNum - 1);
            String to = "0000000" + (toNum + 1);
            int fromPort = from.equals("00000001") ? 1 : 2;
            int toPort = 1;
            System.out.println(String.format("from:%s:%d::%d via %s, To:%s:%d::%d via %s",
                    from,fromPort,sourceVlan,sourceSwitch,
                    to,toPort,destinationVlan,destSwitch));


            Response result = client
                    .target(mininetEndpoint)
                    .path("/checkpingtraffic")
                    .queryParam("srcswitch", from)
                    .queryParam("dstswitch", to)
                    .queryParam("srcport", fromPort)
                    .queryParam("dstport", toPort)
                    .queryParam("srcvlan", sourceVlan)
                    .queryParam("dstvlan", destinationVlan)
                    .request()
                    .get();

            int status = result.getStatus();
            String body = result.readEntity(String.class);
            System.out.println(String.format("======> Response = %s, BODY = %s", result.toString(), body));
            System.out.println(String.format("======> Send traffic Time: %,.3f", getTimeDuration(current)));

            if (body.equals("NOOP"))
                return expectedStatus;
            else
                return status;
        } else {
            return expectedStatus;
        }
    }


    /*
     * NB: This test uses SCAPY on mininet_rest .. and has been deprecated in favor of pingable.
     *      One of the reasons it was deprecated was due to unresolved issues in testing
     *      single switch scenarios.
     */
    @Then("^traffic through (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is forwarded$")
    public void checkTrafficIsForwarded(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                        final String destinationSwitch, final int destinationPort,
                                        final int destinationVlan, final int bandwidth) throws Throwable {
        TimeUnit.SECONDS.sleep(2);
        int status = checkTraffic(sourceSwitch, destinationSwitch, sourceVlan, destinationVlan, 200);
        assertEquals(200, status);
    }

    @Then("^traffic through (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is not forwarded$")
    public void checkTrafficIsNotForwarded(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                           final String destinationSwitch, final int destinationPort,
                                           final int destinationVlan, final int bandwidth) throws Throwable {
        TimeUnit.SECONDS.sleep(2);
        int status = checkTraffic(sourceSwitch, destinationSwitch, sourceVlan, destinationVlan, 503);
        assertNotEquals(200, status);
    }

    /*
     * NB: Pingable uses the native Mininet mechanisms to ping between hosts attached to switches
     */
    @Then("^traffic through (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is pingable$")
    public void checkTrafficIsPingable(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                        final String destinationSwitch, final int destinationPort,
                                        final int destinationVlan, final int bandwidth) throws Throwable {
        TimeUnit.SECONDS.sleep(2);
        int status = checkPingTraffic(sourceSwitch, destinationSwitch, sourceVlan, destinationVlan, 200);
        assertEquals(200, status);
    }

    @Then("^traffic through (.*) (\\d+) (\\d+) and (.*) (\\d+) (\\d+) and (\\d+) is not pingable$")
    public void checkTrafficIsNotPingable(final String sourceSwitch, final int sourcePort, final int sourceVlan,
                                           final String destinationSwitch, final int destinationPort,
                                           final int destinationVlan, final int bandwidth) throws Throwable {
        TimeUnit.SECONDS.sleep(2);
        int status = checkPingTraffic(sourceSwitch, destinationSwitch, sourceVlan, destinationVlan, 503);
        assertNotEquals(200, status);
    }

    @Then("^flows count is (\\d+)$")
    public void checkFlowCount(int expectedFlowsCount) throws Exception {
        int actualFlowCount = getFlowCount(expectedFlowsCount * 2);
        assertEquals(expectedFlowsCount * 2, actualFlowCount);
    }

    /**
     * TODO: This method doesn't validate the flow is stored. Should rename, and consider algorith.
     *  - the algorithm gets the stored flows, if the expected flow isn't there, it'll sleep 2
     *      seconds and then try again. T H A T  I S  I T. Probably need something better wrt
     *      understanding where the request is at, and what an appropriate time to wait is.
     *      One Option is to look at Kafka queues and filter for what we are looking for.
     */
    private List<Flow> validateFlowStored(Flow expectedFlow) throws Exception {
        List<Flow> flows = FlowUtils.dumpFlows();
        flows.forEach(this::resetImmaterialFields);

        if (!flows.contains(expectedFlow) || flows.size() % 2 != 0) {
            TimeUnit.SECONDS.sleep(2);
            flows = FlowUtils.dumpFlows();
            flows.forEach(this::resetImmaterialFields);
        }

        return flows;
    }

    /**
     * @param expectedFlowsCount -1 if unknown
     * @return the count, based on dumpFlows()
     * @throws Exception
     */
    private int getFlowCount(int expectedFlowsCount) throws Exception {
        List<Flow> flows = FlowUtils.dumpFlows();

        // pass in -1 if the count is unknown
        if (expectedFlowsCount >= 0) {
            int arbitraryCount = 3;
            for (int i = 0; i < arbitraryCount; i++) {
                System.out.println(String.format("\n=====> Flow Count is %d, expecting %d",
                        flows.size(), expectedFlowsCount));
                if (expectedFlowsCount == flows.size()) {
                    break;
                }
                TimeUnit.SECONDS.sleep(2);
                flows = FlowUtils.dumpFlows();
            }
            if (expectedFlowsCount != flows.size()) {
                System.out.println(String.format("\n=====> FLOW COUNT doesn't match, flows: %s",
                        flows.toString()));
            }
        }
        return flows.size();
    }

    private void resetImmaterialFields(Flow flow) {
        flow.setTransitVlan(0);
        flow.setMeterId(0);
        flow.setCookie(0);
        flow.setLastUpdated(null);
        flow.setFlowPath(null);
        flow.setState(null);
    }
}
