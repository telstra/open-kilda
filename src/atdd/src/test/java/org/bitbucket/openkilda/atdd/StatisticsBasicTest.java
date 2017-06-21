package org.bitbucket.openkilda.atdd;

import cucumber.api.PendingException;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import static org.junit.Assert.assertNotEquals;


import org.bitbucket.openkilda.topo.TestUtils;
import static org.bitbucket.openkilda.DefaultParameters.mininetEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.topologyEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.opentsdbEndpoint;

public class StatisticsBasicTest {

       public boolean test = false;

       public StatisticsBasicTest() {
       }

       @Given("^Clean setup$")
       public void createCleanSetup() throws Throwable {
            String topology = Resources.toString(getClass().getResource("/topologies/rand-5-20.json"),
                                                 Charsets.UTF_8);
            TestUtils.clearEverything();
            Client client = ClientBuilder.newClient(new ClientConfig());
            Response result = client
                    .target(mininetEndpoint)
                    .path("/create_random_linear_topology")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(topology, MediaType.APPLICATION_JSON));
       }

       @When("^any topology is created$")
       public void topologyIsCreated() throws Throwable {
            Client client = ClientBuilder.newClient(new ClientConfig());
            String result = client
                    .target(topologyEndpoint)
                    .path("/api/v1/topology/network")
                    .request()
                    .get(String.class);
            assertNotEquals(result, "{\"nodes\": []}");
       }

       @Then("^data go to database$")
       public void dataCreated() throws Throwable {
            Client client = ClientBuilder.newClient(new ClientConfig());
            String result = client
                    .target(opentsdbEndpoint)
                    .path("/api/query")
                    .queryParam("start","100h-ago")
                    .queryParam("m","count:collisions")
                    .request()
                    .get(String.class);
            assertNotEquals(result.length(), 0);
       }

       @Then("^database keeps growing$")
       public void database_keeps_growing() throws Throwable {
           // NOTE: the test checks collisions, however any timeseries
           // could be checked.
           Client client = ClientBuilder.newClient(new ClientConfig());
           String result1 = client
                   .target(opentsdbEndpoint)
                   .path("/api/query")
                   .queryParam("start","100h-ago")
                   .queryParam("m","count:collisions")
                   .request()
                   .get(String.class);
           TimeUnit.SECONDS.sleep(5);
           String result2 = client
                   .target(opentsdbEndpoint)
                   .path("/api/query")
                   .queryParam("start","100h-ago")
                   .queryParam("m","count:collisions")
                   .request()
                   .get(String.class);
           assertNotEquals(result1.length(), result2.length());
       }
}
