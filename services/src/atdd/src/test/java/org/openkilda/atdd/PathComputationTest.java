/* Copyright 2018 Telstra Open Source
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

import cucumber.api.PendingException;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import org.openkilda.topo.TopologyHelp;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * PathComputationTest implements a majority of the cucumber tests for the Path Computation Engine
 * (PCE). The interesting scenarios are around the following areas:
 *
 *  - validate that kilda honors the various path computation scenarios
 *  - there should also be path edge cases that are tested, in order to validate the user
 *      experience - eg when a flow is requested that doesn't exist (bandwidth limitation or just
 *      no path). However, a few are already implemented in FlowPathTest.
 *
 * In order to test the scenarios, we use a special topology:
 *
 *      1 - 2 - 3 - 4 - 5 - 6 - 7
 *          2 -- 8 - - 9 -- 6
 *          2 - - - A - - - 6
 *          2 - - - - - - - 6
 *
 * This is to ensure that, as we vary the hops (path length) we also very the the path used.
 *
 *  - For hop count, 2-6 should win.
 *  - For latency, 2-A-6 should win (we'll configure it as such)
 *  - For cost, 2-8-9-6 should win (again, we'll configure it as such)
 *  - For external, 2-3-4-5-6 should win (based on configuration)
 *
 * To run just the tests in this file:
 *
 *      $> make atdd tags="@PCE"
 *
 * Other Notes:
 *  - Look at FlowPathTest for some of the edge cases related to not enough bandwidth, etc.
 */
public class PathComputationTest {
    private static final String fileName = "topologies/pce-spider-topology.json";

    //
    // TODO: ensure bandwidth tests are part of this .. so we confirm policy & bandwidth
    // TODO: Create the Expected Flows for each scenario / policy
    // TODO: Can we confirm Neo4J, FlowCache, and FL/Switches?
    //

    @Given("^a spider web topology with endpoints A and B$")
    public void a_multi_path_topology() throws Throwable {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        String json = new String(Files.readAllBytes(file.toPath()));
        assertTrue(TopologyHelp.createMininetTopology(json));
        // Should also wait for some of this to come up

    }

    @Given("^no link properties$")
    public void no_link_properties() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^link costs are uploaded through the NB API$")
    public void link_costs_are_uploaded_through_the_NB_API() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^link costs can be downloaded$")
    public void link_costs_can_be_downloaded() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^link properties reflect what is in the link properties table$")
    public void link_properties_reflect_what_is_in_the_link_properties_table() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^link costs can be deleted$")
    public void link_costs_can_be_deleted() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^link costs are updated through the NB API$")
    public void link_costs_are_updated_through_the_NB_API() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a flow request is made between A and B with HOPS$")
    public void a_flow_request_is_made_between_A_and_B_with_HOPS() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the path matches the HOPS$")
    public void the_path_matches_the_HOPS() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the path between A and B is pingable$")
    public void the_path_between_A_and_B_is_pingable() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a flow request is made between A and B with LATENCY$")
    public void a_flow_request_is_made_between_A_and_B_with_LATENCY() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the path matches the LATENCY$")
    public void the_path_matches_the_LATENCY() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a flow request is made between A and B with COST$")
    public void a_flow_request_is_made_between_A_and_B_with_COST() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the path matches the COST$")
    public void the_path_matches_the_COST() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a flow request is made between A and B with EXTERNAL(\\d+)$")
    public void a_flow_request_is_made_between_A_and_B_with_EXTERNAL(int arg1) throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the path matches the EXTERNAL(\\d+)$")
    public void the_path_matches_the_EXTERNAL(int arg1) throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^one or more links are added to the spider web topology$")
    public void one_or_more_links_are_added_to_the_spider_web_topology() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the new links will have the cost properties added if the cost id matches$")
    public void the_new_links_will_have_the_cost_properties_added_if_the_cost_id_matches() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }


}
