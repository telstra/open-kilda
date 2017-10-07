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

package org.bitbucket.openkilda.atdd;

import static org.bitbucket.openkilda.DefaultParameters.opentsdbEndpoint;
import static org.bitbucket.openkilda.flow.FlowUtils.getTimeDuration;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

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
    public boolean test = false;

    public StatisticsBasicTest() {
    }

    private List<Metric> getNumberOfDatapoints() throws Throwable {
        System.out.println("\n==> OpenTSDB Metrics request");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(opentsdbEndpoint)
                .path("/api/query")
                .queryParam("start", "24h-ago")
                .queryParam("m", "sum:pen.switch.tx-bytes")
                .queryParam("timezone", "Australia/Melbourne")
                .request().get();

        System.out.println(String.format("===> Response = %s", response));
        System.out.println(String.format("===> OpenTSDB Metrics Time: %,.3f", getTimeDuration(current)));

        List<Metric> metrics = new ObjectMapper().readValue(
                response.readEntity(String.class), new TypeReference<List<Metric>>() {});

        System.out.println(String.format("====> Metrics = %s", metrics));

        return metrics;
    }

    @Then("^data go to database$")
    public void dataCreated() throws Throwable {
        TimeUnit.SECONDS.sleep(15);
        List<Metric> result = getNumberOfDatapoints();

        assertFalse(result.isEmpty());
    }

    @Then("^database keeps growing$")
    public void database_keeps_growing() throws Throwable {
        List<Metric> firstResult = getNumberOfDatapoints();
        TimeUnit.SECONDS.sleep(15);
        List<Metric> secondResult = getNumberOfDatapoints();

        assertNotEquals(firstResult, secondResult);
    }
}
