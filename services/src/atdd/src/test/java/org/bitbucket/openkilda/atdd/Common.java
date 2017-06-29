package org.bitbucket.openkilda.atdd;

import cucumber.api.java.en.Given;

import org.bitbucket.openkilda.flow.FlowUtils;
import org.bitbucket.openkilda.topo.TestUtils;

/**
 * Created by carmine on 5/3/17.
 */
public class Common {

    /**
     * Scenarios are not parallel .. so use singleton pattern for now.
     */
    public static Common latest;

    public String   kildaHost = "localhost";
    
    
    public Common(){
        latest = this;
    }

    public Common setHost(String kildaHost){
        this.kildaHost = kildaHost;
        return this;
    }
    /**
     * This code will make sure there aren't any topologies
     */
    @Given("^a clean controller$")
    public void a_clean_controller() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        setHost(System.getProperty("kilda.host",kildaHost));

        TestUtils.clearEverything(kildaHost);
    }

    /**
     * This code will make sure there aren't any flows
     */
    @Given("^a clean flow topology$")
    public void a_clean_flow_topology() throws Throwable {
        FlowUtils.cleanupFlows();
    }
}
