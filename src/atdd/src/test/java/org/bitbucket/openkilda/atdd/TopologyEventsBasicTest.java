package org.bitbucket.openkilda.atdd;

import cucumber.api.PendingException;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;


/**
 * Created by carmine on 5/1/17.
 */
public class TopologyEventsBasicTest {

    public boolean test = false;

    /**
     * This is just the default. It can be overwritten on the command line:
     * <code>
     *      mvn -DargLine="-Dkilda.ip=1.2.3.4" test
     * </code>
     */
    public String kildaEndpoint = "127.0.0.1";

    /**
     * This code will make sure there aren't any topologies:
     * 0) drop workflows (ie storm topologies)
     * 1) delete things in mininet
     * 2) delete things in topology engine (and anywhere else)
     * 3) verify everything is gone
     * 4) bring up workflows again.
     */
    @Given("^a clean controller$")
    public void a_clean_controller() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        String kildaEndpoint = System.getProperty("kilda.ip","127.0.0.1");
        System.out.println("kildaEndpoint = " + kildaEndpoint);

        throw new PendingException();
    }

    @When("^multiple links exist between all switches$")
    public void multiple_links_exist_between_all_switches() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a link is dropped in the middle$")
    public void a_link_is_dropped_in_the_middle() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the link will have no health checks$")
    public void the_link_will_have_no_health_checks() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the link disappears from the topology engine\\.$")
    public void the_link_disappears_from_the_topology_engine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a link is added in the middle$")
    public void a_link_is_added_in_the_middle() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the link will have health checks$")
    public void the_link_will_have_health_checks() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the link appears in the topology engine\\.$")
    public void the_link_appears_in_the_topology_engine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a switch is dropped in the middle$")
    public void a_switch_is_dropped_in_the_middle() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^all links through the dropped switch will have no health checks$")
    public void all_links_through_the_dropped_switch_will_have_no_health_checks() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the links disappear from the topology engine\\.$")
    public void the_links_disappear_from_the_topology_engine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the switch disappears from the topology engine\\.$")
    public void the_switch_disappears_from_the_topology_engine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^a switch is added at the edge$")
    public void a_switch_is_added_at_the_edge() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^links are added between the new switch and its neighbor$")
    public void links_are_added_between_the_new_switch_and_its_neighbor() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^all links through the added switch will have health checks$")
    public void all_links_through_the_added_switch_will_have_health_checks() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the links appear in the topology engine\\.$")
    public void the_links_appear_in_the_topology_engine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the switch appears in the topology engine\\.$")
    public void the_switch_appears_in_the_topology_engine() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

}
