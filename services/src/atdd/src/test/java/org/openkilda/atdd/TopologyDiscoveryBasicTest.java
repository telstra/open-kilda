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
import static org.junit.Assert.assertTrue;
import static org.openkilda.DefaultParameters.trafficEndpoint;
import static org.openkilda.flow.FlowUtils.getTimeDuration;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.glassfish.jersey.client.ClientConfig;
import org.openkilda.topo.ITopology;
import org.openkilda.topo.TestUtils;
import org.openkilda.topo.builders.TestTopologyBuilder;
import org.openkilda.topo.TopologyHelp;
import org.openkilda.topo.TopologyPrinter;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

public class TopologyDiscoveryBasicTest {

    public long pre_start;
    public long start;
    public long finish;
    public ITopology expected;
    public static int pre_start_handicap = 10000; // milliseconds .. how long a deploy should take.

    protected void deploy_toplogy(ITopology t) throws Throwable {
        expected = t;
        String json = TopologyPrinter.toMininetJson(t);
        pre_start = System.currentTimeMillis();
        assertTrue(TopologyHelp.createMininetTopology(json));
        start = System.currentTimeMillis();
    }

	@Given("^a random linear topology of (\\d+) switches$")
	public void a_random_linear_topology_of(int numSwitches) throws Throwable {
        deploy_toplogy(TestTopologyBuilder.buildLinearTopo(numSwitches));
	}

	@Given("^a random tree topology with depth of (\\d+) and fanout of (\\d+)$")
	public void a_random_full_mesh_topology_of(int depth, int fanout) throws Throwable {
        deploy_toplogy(TestTopologyBuilder.buildTreeTopo(depth,fanout));
	}

	@When("^the controller learns the topology$")
	public void the_controller_learns_the_topology() throws Throwable {
        // NB: translateTopoEngTopo includes some heuristics regarding waiting for things
        // TODO: pass the convergence time to this function, since it is the one that loops
        //       and times out; currently it just has a default time; but should be based on the test.
        //       alternatively, as is currently the case, it keeps going as long as there is change
        //       and/or the expected topology is reached.
        ITopology actual = TestUtils.translateTopoEngTopo(expected);
        TestUtils.validateTopos(expected,actual);
        finish = System.currentTimeMillis();
	}

	@Then("^the controller should converge within (\\d+) milliseconds$")
	public void the_controller_should_converge_within_milliseconds(int delta) throws Throwable {
        if (!(delta >= (finish - start))){
            System.out.println(String.format("Failed finish-start convergence: delta_ma:%d, actual:%d", delta, (finish-start)));
        }
        assertTrue(delta >= (finish - start));
	    // This next test is a little suspect .. it is unclear how much latency is
        // introduced through mininet. Hypothetically, if mininet took 1 second per switch,
        // then I'd expect the delta between start and finish to be real small, 1-2 seconds, even
        // for a 1000 switches.
        //
        // We'll test the pre-start too. This will have to be revisited somehow.
        // The best test will be for the switches to all exist and reach out simultaneously.
        delta += pre_start_handicap; // TODO: make handicap a factor of topology size
        if (!(delta >= (finish - pre_start))){
            System.out.println(String.format("Failed finish-pre_start convergence test: delta_ma:%d, actual:%d", delta, (finish-pre_start)));
        }
        assertTrue(delta >= (finish - pre_start));
	}


    @Then("^the topology is not changed")
    public void validate_topology() throws Throwable {
        ITopology actual = TestUtils.translateTopoEngTopo(expected);
        TestUtils.validateTopos(expected,actual);
    }

    @When("^send malformed lldp packet$")
    public void sendMalformedLldpPacket() throws Throwable {
            System.out.println("=====> Send malformed packet");

            long current = System.currentTimeMillis();
            Client client = ClientBuilder.newClient(new ClientConfig());
            Response result = client
                    .target(trafficEndpoint)
                    .path("/send_malformed_packet")
                    .request()
                    .post(null);
            System.out.println(String.format("======> Response = %s", result.toString()));
            System.out.println(String.format("======> Send malformed packet Time: %,.3f", getTimeDuration(current)));

        assertEquals(200, result.getStatus());

    }
}
