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

import static com.google.common.base.Charsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.DefaultParameters.trafficEndpoint;
import static org.openkilda.flow.FlowUtils.getTimeDuration;
import static org.openkilda.flow.FlowUtils.isTrafficTestsEnabled;

import org.openkilda.KafkaUtils;
import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.ctrl.state.OFELinkBoltState;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateManager;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.SwitchId;
import org.openkilda.topo.TestUtils;
import org.openkilda.topo.TopologyHelp;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.messages.Container;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.glassfish.jersey.client.ClientConfig;

import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;


public class StormTopologyLcm {

    private static final String WFM_CONTAINER_NAME = "/wfm";

    private Container wfmContainer;
    private DockerClient dockerClient;
    private KafkaUtils kafkaUtils;
    private DumpStateManager expectedStateDumpsFromBolts;
    private final String flowId = FlowUtils.getFlowName("simple-flow");

    public StormTopologyLcm() throws Exception {
        dockerClient = DefaultDockerClient.fromEnv().build();
        wfmContainer = dockerClient.listContainers(ListContainersParam.allContainers())
                .stream()
                .filter(container ->
                        container.names().contains(WFM_CONTAINER_NAME))
                .findFirst().orElseThrow(
                        () -> new IllegalStateException("Can't find wfm container"));
        kafkaUtils = new KafkaUtils();
    }

    /**
     * We restart wfm container which kill all storm topologies and send new to storm.
    */
    @When("^storm topologies are restarted$")
    public void reloadStormTopologies() throws Exception {

        System.out.println("\n=====> Recreate wfm container");
        dockerClient.restartContainer(wfmContainer.id());
        dockerClient.waitContainer(wfmContainer.id());
    }

    /**
     * Test initial function, clear all then load basic network topology and create flow.
    */
    @Given("^active simple network topology with two switches and flow$")
    public void activeSimpleNetworkTopologyWithTwoSwitchesAndFlow() throws Throwable {
        // clearEverything
        TestUtils.clearEverything(StringUtils.EMPTY);
        FlowUtils.cleanupFlows();

        // Load topo from file and send to mininet
        String topology = IOUtils.toString(this.getClass().getResourceAsStream(
                "/topologies/simple-topology.json"), UTF_8);
        assertTrue(TopologyHelp.createMininetTopology(topology));

        // Create and check flow
        FlowPayload flowPayload = new FlowPayload(flowId,
                new FlowEndpointPayload(new SwitchId(1L), 1, 100),
                new FlowEndpointPayload(new SwitchId(2L), 1, 100),
                10000, false, false, flowId, null, FlowState.UP.getState());

        FlowPayload response = null;
        for (int i = 0; i < 10; ++i) {
            response = FlowUtils.putFlow(flowPayload);
            if (response != null) {
                break;
            }
            TimeUnit.SECONDS.sleep(1);
        }

        assertNotNull(response);
        response.setLastUpdated(null);
        assertEquals(flowPayload, response);

        // Check traffic on new flow
        assertTrue(trafficIsOk(true));

        // Save bolt state for compare it with new one after restart
        expectedStateDumpsFromBolts = kafkaUtils.getStateDumpsFromBolts();
    }

    /**
     * Just check that flow is here and it in UP state.
     */
    @Then("^network topology in the same state$")
    public void networkTopologyInTheSameState() throws Throwable {

        FlowIdStatusPayload flowStatus = null;
        for (int i = 0; i < 6; ++i) {
            flowStatus = FlowUtils.getFlowStatus(flowId);
            if (flowStatus != null && FlowState.UP == flowStatus.getStatus()) {
                break;
            }
            TimeUnit.SECONDS.sleep(10);
        }

        assertNotNull(flowStatus);
        assertEquals(FlowState.UP, flowStatus.getStatus());
    }

    /**
     * We compare old inner state of cache bolt and OFELinkBolt with new state after reload
     * topologies.
     */
    @And("^all storm topologies in the same state$")
    public void allStormTopologiesInTheSameState() {

        DumpStateManager actualSateDumpsFromBolts = kafkaUtils.getStateDumpsFromBolts();

        // OFELinkBolt
        OFELinkBoltState actualOfeLinkBoltState = actualSateDumpsFromBolts.getOfeLinkBoltState();
        OFELinkBoltState expectedOfeLinkBoltState = expectedStateDumpsFromBolts.getOfeLinkBoltState();
        assertTrue(CollectionUtils.isEqualCollection(expectedOfeLinkBoltState.getDiscovery(),
                actualOfeLinkBoltState.getDiscovery()));
    }

    @And("^traffic flows through flow$")
    public void trafficFlowsThroughFlow() throws Throwable {
        assertTrue(trafficIsOk(true));
    }

    private boolean trafficIsOk(boolean expectedResult) {

        //TODO: move that to utils
        if (isTrafficTestsEnabled()) {
            System.out.println("=====> Send traffic");

            long current = System.currentTimeMillis();
            Client client = ClientBuilder.newClient(new ClientConfig());
            Response result = client
                    .target(trafficEndpoint)
                    .path("/checkflowtraffic")
                    .queryParam("srcswitch", "switch1")
                    .queryParam("dstswitch", "switch2")
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
}
