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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.DefaultParameters.trafficEndpoint;
import static org.openkilda.flow.FlowUtils.getTimeDuration;
import static org.openkilda.flow.FlowUtils.isTrafficTestsEnabled;

import org.openkilda.LinksUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.SwitchId;
import org.openkilda.topo.TopologyHelp;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import org.glassfish.jersey.client.ClientConfig;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

public class FlowFfrTest {
    private static final int DEFAULT_DISCOVERY_INTERVAL = 10;
    private static final SwitchId sourceSwitch = new SwitchId(2L);
    private static final SwitchId destinationSwitch = new SwitchId(7L);
    private static final Integer sourcePort = 1;
    private static final Integer destinationPort = 2;
    private static final Integer sourceVlan = 1000;
    private static final Integer destinationVlan = 1000;
    private static final int bandwidth = 1000;

    @Given("^basic multi-path topology$")
    public void multiPathTopology() throws Throwable {
        String fileName = "topologies/barebones-topology.json";
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException(String.format("No such topology json file: %s", fileName));
        }

        File file = new File(resource.getFile());
        String json = new String(Files.readAllBytes(file.toPath()));
        assertTrue(TopologyHelp.createMininetTopology(json));
    }

    @When("^a flow (.*) is successfully created$")
    public void successfulFlowCreation(String flowId) throws Throwable {
        FlowPayload flowPayload = new FlowPayload(FlowUtils.getFlowName(flowId),
                new FlowEndpointPayload(sourceSwitch, sourcePort, sourceVlan),
                new FlowEndpointPayload(destinationSwitch, destinationPort, destinationVlan),
                bandwidth, false, false, flowId, null, FlowState.UP.getState());

        FlowPayload response = FlowUtils.putFlow(flowPayload);
        assertNotNull(response);
        response.setLastUpdated(null);

        assertEquals(flowPayload, response);
        System.out.println(response.toString());
        TimeUnit.SECONDS.sleep(5);
    }

    @When("^traffic flows through (.+) flow$")
    public void trafficFlows(String flowId) throws Throwable {
        assertTrue(trafficIsOk(true));
    }

    @When("^traffic does not flow through (.+) flow$")
    public void trafficNotFlows(String flowId) throws Throwable {
        assertFalse(trafficIsOk(false));
    }

    @When("^flow (.+) path is shortest$")
    public void flowPathIsShortest(String flowId) throws Throwable {
        String flowName = FlowUtils.getFlowName(flowId);

        FlowPathPayload payload = getFlowPath(flowName, FlowPathTest.expectedShortestPath.getLeft());
        assertNotNull(payload);

        assertEquals(flowName, payload.getId());
        assertEquals(FlowPathTest.expectedShortestPath.getLeft(), payload.getForwardPath());
    }

    @When("^flow (.+) path is alternate$")
    public void flowPathIsAlternate(String flowId) throws Throwable {
        String flowName = FlowUtils.getFlowName(flowId);

        FlowPathPayload payload = getFlowPath(flowName, FlowPathTest.expectedAlternatePath.getLeft());
        assertNotNull(payload);

        assertEquals(flowName, payload.getId());
        assertEquals(FlowPathTest.expectedAlternatePath.getLeft(), payload.getForwardPath());
    }

    @When("^a switch (.*) port (\\d+) is disabled")
    public void switchPortDisable(String switchId, int portNo) throws Throwable {
        String switchName = getSwitchName(switchId);
        assertTrue(portDown(switchName, String.valueOf(portNo)));
        TimeUnit.SECONDS.sleep(DEFAULT_DISCOVERY_INTERVAL);
    }

    @When("^a switch (.*) port (\\d+) is enabled")
    public void switchPortEnable(String switchId, int portNo) throws Throwable {
        String switchName = getSwitchName(switchId);
        assertTrue(portUp(switchName, String.valueOf(portNo)));
        TimeUnit.SECONDS.sleep(DEFAULT_DISCOVERY_INTERVAL);
    }

    @When("^a switch (.+) is disconnected$")
    public void switchDisconnected(String switchId) throws Exception {
        String switchName = getSwitchName(switchId);
        assertTrue(disconnectSwitch(switchName));
        TimeUnit.SECONDS.sleep(DEFAULT_DISCOVERY_INTERVAL);
    }

    @When("^a switch (.+) is connected$")
    public void switchConnected(String switchId) throws Exception {
        String controller = getController("topologies/multi-path-topology.json");
        String switchName = getSwitchName(switchId);
        assertTrue(connectSwitch(switchName, controller));
        TimeUnit.SECONDS.sleep(DEFAULT_DISCOVERY_INTERVAL);
    }

    @When("^an isl switch (.*) port (\\d+) is failed$")
    public void anIslSwitchPortFails(String switchId, int portNo) throws Throwable {
        String switchName = getSwitchName(switchId);
        assertTrue(LinksUtils.islFail(switchName, String.valueOf(portNo)));
        TimeUnit.SECONDS.sleep(DEFAULT_DISCOVERY_INTERVAL);
    }

    @When("^an isl switch (.*) port (\\d+) is discovered")
    public void anIslSwitchPortDiscovered(String switchId, int portNo) throws Throwable {
        String switchName = getSwitchName(switchId);
        assertTrue(islDiscovered(switchName, String.valueOf(portNo)));
        TimeUnit.SECONDS.sleep(DEFAULT_DISCOVERY_INTERVAL);
    }

    private FlowPathPayload getFlowPath(String flowName, PathInfoData expectedPath) throws Exception {
        FlowPathPayload payload = FlowUtils.getFlowPath(flowName);
        for (int i = 0; i < 10; i++) {
            payload = FlowUtils.getFlowPath(flowName);
            if (payload != null && expectedPath.equals(payload.getForwardPath())) {
                break;
            }
            TimeUnit.SECONDS.sleep(2);
        }
        return payload;
    }

    private String getSwitchName(String switchId) {
        String dpid = switchId.replaceAll(":", "");
        Integer number = Integer.parseInt(dpid);
        return String.format("s%d", number);
    }

    private String getController(String topologyFile) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(topologyFile);
        if (resource == null) {
            throw new IllegalArgumentException(String.format("No such topology json file: %s", topologyFile));
        }

        File file = new File(resource.getFile());
        String json = new String(Files.readAllBytes(file.toPath()));
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(json);
        JSONArray controllers = (JSONArray) jsonObject.get("controllers");

        JSONObject controller = (JSONObject) controllers.get(0);
        return  (String) controller.get("host");
    }

    private boolean trafficIsOk(boolean expectedResult) {
        if (isTrafficTestsEnabled()) {
            System.out.println("=====> Send traffic");

            long current = System.currentTimeMillis();
            Client client = ClientBuilder.newClient(new ClientConfig());
            Response result = client
                    .target(trafficEndpoint)
                    .path("/checkflowtraffic")
                    .queryParam("srcswitch", "s1")
                    .queryParam("dstswitch", "s8")
                    .queryParam("srcport", "1")
                    .queryParam("dstport", "1")
                    .queryParam("srcvlan", "1000")
                    .queryParam("dstvlan", "1000")
                    .request()
                    .get();

            System.out.println(String.format("======> Response = %s", result.toString()));
            System.out.println(String.format("======> Send traffic Time: %,.3f", getTimeDuration(current)));

            return result.getStatus() == 200;
        } else {
            return expectedResult;
        }
    }

    private boolean disconnectSwitch(String switchName) {
        System.out.println("\n==> Disconnect Switch");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/knockoutswitch")
                .queryParam("switch", switchName)
                .request()
                .post(Entity.json(""));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Disconnect Switch Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    private boolean connectSwitch(String switchName, String controller) {
        System.out.println("\n==> Connect Switch");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/reviveswitch")
                .queryParam("switch", switchName)
                .queryParam("controller", "tcp:" + controller + ":6653")
                .request()
                .post(Entity.json(""));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Connect Switch Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    private boolean islDiscovered(String switchName, String portNo) {
        System.out.println("\n==> Set ISL Discovered");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/restorelink")
                .queryParam("switch", switchName)
                .queryParam("port", portNo)
                .request()
                .post(Entity.json(""));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Set ISL Discovered Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    private boolean portDown(String switchName, String portNo) {
        System.out.println("\n==> Set Port Down");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/port/down")
                .queryParam("switch", switchName)
                .queryParam("port", portNo)
                .request()
                .post(Entity.json(""));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Set Port Down Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    private boolean portUp(String switchName, String portNo) {
        System.out.println("\n==> Set Port Up");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/port/up")
                .queryParam("switch", switchName)
                .queryParam("port", portNo)
                .request()
                .post(Entity.json(""));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Set Port Up Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }
}
