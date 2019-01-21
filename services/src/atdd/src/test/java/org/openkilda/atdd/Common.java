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

import cucumber.api.java.en.Given;
//import org.openkilda.flow.FlowUtils;
//import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.topo.TestUtils;

/**
 * Created by carmine on 5/3/17.
 */
public class Common {

    /**
     * Scenarios are not parallel .. so use singleton pattern for now.
     */
    public static Common latest;

    public String kildaHost = "localhost";


    public Common() {
        latest = this;
    }

    public Common setHost(String kildaHost) {
        this.kildaHost = kildaHost;
        return this;
    }

    /**
     * This code will make sure there aren't any topologies
     */
    @Given("^a clean controller$")
    public void a_clean_controller() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        setHost(System.getProperty("kilda.host", kildaHost));

        TestUtils.clearEverything(kildaHost);
    }

    /**
     * This code will make sure there aren't any flows
     */
    /*@Given("^a clean flow topology$")
    public void a_clean_flow_topology() throws Throwable {
        FlowUtils.cleanupFlows();

        FeatureTogglePayload features = new FeatureTogglePayload(true, true, true, true, true, true,
                true);
        FlowUtils.updateFeaturesStatus(features);
    }*/
}
