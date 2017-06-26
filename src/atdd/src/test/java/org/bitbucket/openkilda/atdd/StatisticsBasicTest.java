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

       private int getNumberOfDatapoints() throws Throwable {
           Client client = ClientBuilder.newClient(new ClientConfig());
           int result = client
                   .target(opentsdbEndpoint)
                   .path("/api/query")
                   .queryParam("start","100h-ago")
                   .queryParam("m","count:collisions")
                   .request()
                   .get(String.class).length();
           return result;
       }

       @Then("^data go to database$")
       public void dataCreated() throws Throwable {
           int result = getNumberOfDatapoints();
           assertNotEquals(result, 0);
       }

       @Then("^database keeps growing$")
       public void database_keeps_growing() throws Throwable {
           int result1 = getNumberOfDatapoints();
           // floodlight-modules statistics gathering interval
           TimeUnit.SECONDS.sleep(10);
           int result2 = getNumberOfDatapoints();
           assertNotEquals(result1, result2);
       }
}
