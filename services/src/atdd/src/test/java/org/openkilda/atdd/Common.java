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

import org.openkilda.flow.FlowUtils;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.topo.TestUtils;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;

import java.util.concurrent.TimeUnit;

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
     * This code will make sure there aren't any topologies.
     */
    @Given("^a clean controller$")
    public void cleanController() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        setHost(System.getProperty("kilda.host", kildaHost));

        TestUtils.clearEverything(kildaHost);
    }

    /**
     * This code will make sure there aren't any flows.
     */
    @Given("^a clean flow topology$")
    public void cleanFlowTopology() throws Throwable {
        FlowUtils.cleanupFlows();

        FeatureTogglePayload features = new FeatureTogglePayload(true, true, true, true, true, true,
                true);
        FlowUtils.updateFeaturesStatus(features);
    }

    @And("wait for (\\d+) seconds?.*")
    public void delay(int seconds) throws InterruptedException {
        System.out.println(String.format("Sleeping for %s seconds..", seconds));
        TimeUnit.SECONDS.sleep(seconds);
    }
}
