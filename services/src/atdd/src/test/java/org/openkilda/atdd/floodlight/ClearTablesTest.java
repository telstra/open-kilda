package org.openkilda.atdd.floodlight;


import static com.google.common.base.Charsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ExecCreation;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.openkilda.DefaultParameters;
import org.openkilda.domain.floodlight.FlowRules;
import org.openkilda.topo.TestUtils;
import org.openkilda.topo.TopologyHelp;

import java.util.concurrent.TimeUnit;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class ClearTablesTest {
    private static final Logger LOGGER = Logger.getLogger(ClearTablesTest.class);

    private static final String FLOODLIGHT_CONTAINER_PREFIX = "kilda/floodlight";
    private static final String MININET_CONTAINER_PREFIX = "kilda/mininet";
    private static final String SWITCH_NAME = "switch1";
    private static final String SWITCH_ID = "00:01:00:00:00:00:00:01";
    private static final String ACL_RULES_PATH = String.format("wm/core/switch/%s/flow/json", SWITCH_ID);
    private static final String COOKIE = "0x700000000000000";
    private static final String RULE = String.format("cookie=%s,dl_dst=ff:ff:ff:ff:ff:ff actions=output:2", COOKIE);
    private static final Client CLIENT = ClientBuilder.newClient(new ClientConfig());

    private Container floodlightContainer;
    private DockerClient dockerClient;

    @Given("^started floodlight container")
    public void givenStartedContainer() throws Exception {
        TestUtils.clearEverything();
        dockerClient = DefaultDockerClient.fromEnv().build();
        floodlightContainer = dockerClient.listContainers()
                .stream()
                .filter(container ->
                        container.image().startsWith(FLOODLIGHT_CONTAINER_PREFIX))
                .findFirst().orElseThrow(() -> new IllegalStateException("Floodlight controller should be active"));
        assertNotNull(floodlightContainer);
    }

    @Given("^created simple topology from two switches")
    public void createTopology() throws Exception {
        String topology =
                IOUtils.toString(this.getClass().getResourceAsStream("/topologies/simple-topology.json"), UTF_8);
        assertTrue(TopologyHelp.CreateMininetTopology(topology));
    }

    @Given("^added custom flow rules")
    public void addRules() throws Exception {
        Container mininetContainer = dockerClient.listContainers()
                .stream()
                .filter(container -> container.image().startsWith(MININET_CONTAINER_PREFIX))
                .findFirst().orElseThrow(() -> new IllegalStateException("Floodlight controller should be active"));

        final String[] commands = {"ovs-ofctl", "-O", "Openflow13", "add-flow", SWITCH_NAME, RULE};
        ExecCreation execCreation = dockerClient.execCreate(mininetContainer.id(), commands,
                DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());

        final LogStream output = dockerClient.execStart(execCreation.id());
        final String execOutput = output.readFully();
        assertTrue(StringUtils.isEmpty(execOutput));
    }

    @When("^floodlight controller is reloaded")
    public void reloadFloodlight() throws Exception {
        dockerClient.restartContainer(floodlightContainer.id());
        await().atMost(10, TimeUnit.SECONDS)
                .until(this::isFloodlightAlive);
    }

    @Then("^flow rules should not be cleared up")
    public void checkFlowRules() {
        FlowRules result = CLIENT
                .target(DefaultParameters.FLOODLIGHT_ENDPOINT)
                .path(ACL_RULES_PATH)
                .request()
                .get(FlowRules.class);

        assertTrue("Floodlight should send flow rules", result != null && result.getFlows() != null);
        String expectedCookie = String.valueOf(Long.parseLong(COOKIE.substring(2), 16));
        assertThat(result.getFlows(), hasItem(hasProperty("cookie", is(expectedCookie))));
        dockerClient.close();
    }

    private boolean isFloodlightAlive() {
        FlowRules result;
        try {
            result = CLIENT
                    .target(DefaultParameters.FLOODLIGHT_ENDPOINT)
                    .path(ACL_RULES_PATH)
                    .request()
                    .get(FlowRules.class);

            return result != null && result.getFlows() != null;
        } catch (ProcessingException e) {
            LOGGER.trace("Floodlight is still unavailable");
            return false;
        }
    }
}
