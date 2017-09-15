package org.bitbucket.openkilda.atdd;

import static org.bitbucket.openkilda.DefaultParameters.opentsdbEndpoint;
import static org.bitbucket.openkilda.flow.FlowUtils.getTimeDuration;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.messaging.model.Metric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cucumber.api.java.en.Then;
import org.glassfish.jersey.client.ClientConfig;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

public class StatisticsBasicTest {
    private static final int TIMEOUT = 15;

    public boolean test = false;

    public StatisticsBasicTest() {
    }

    private List<Metric> getNumberOfDatapoints() throws Throwable {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response response = client
                .target(opentsdbEndpoint)
                .path("/api/query")
                .queryParam("start", "24h-ago")
                .queryParam("m", "sum:pen.switch.tx-bytes")
                .queryParam("timezone", "Australia/Melbourne")
                .request().get();

        System.out.println("\n== OpenTSDB Metrics request");
        System.out.println(String.format("==> response = %s", response));
        System.out.println(String.format("==> OpenTSDB Metrics Time: %,.3f", getTimeDuration(current)));

        List<Metric> metrics = new ObjectMapper().readValue(
                response.readEntity(String.class), new TypeReference<List<Metric>>() {
                });

        System.out.println(String.format("===> Metrics = %s", metrics));

        return metrics;
    }

    @Then("^data go to database$")
    public void dataCreated() throws Throwable {
        TimeUnit.SECONDS.sleep(TIMEOUT);
        List<Metric> result = getNumberOfDatapoints();

        assertFalse(result.isEmpty());
    }

    @Then("^database keeps growing$")
    public void database_keeps_growing() throws Throwable {
        List<Metric> firstResult = getNumberOfDatapoints();
        TimeUnit.SECONDS.sleep(TIMEOUT);
        List<Metric> secondResult = getNumberOfDatapoints();

        assertNotEquals(firstResult, secondResult);
    }
}
