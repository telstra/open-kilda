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

package org.openkilda.atdd.staging.steps;

import static org.junit.Assert.assertEquals;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.aswitch.ASwitchService;
import org.openkilda.atdd.staging.service.aswitch.model.ASwitchFlow;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.steps.helpers.TopologyUnderTest;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.PathNode;

import cucumber.api.java.After;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;

public class IslSteps {

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private ASwitchService aswitchService;

    @Autowired
    @Qualifier("topologyUnderTest")
    private TopologyUnderTest topologyUnderTest;

    List<TopologyDefinition.Isl> changedIsls = new ArrayList<>();

    @Autowired
    @Qualifier("topologyEngineRetryPolicy")
    private RetryPolicy retryPolicy;

    /**
     * Breaks the connection of given ISL by removing rules from intermediate switch.
     * Breaking ISL this way is not equal to physically unplugging the cable
     * because port_down event is not being produced
     */
    @When("ISL between switches goes down")
    public void transitIslDown() {
        topologyUnderTest.getFlowIsls().forEach((flow, isls) -> {
            TopologyDefinition.Isl islToRemove = isls.stream().filter(isl -> isl.getAswitch() != null)
                    .findFirst().get();
            TopologyDefinition.ASwitch aswitch = islToRemove.getAswitch();
            ASwitchFlow aswFlowForward = new ASwitchFlow(aswitch.getInPort(), aswitch.getOutPort());
            ASwitchFlow aswFlowReverse = new ASwitchFlow(aswitch.getOutPort(), aswitch.getInPort());
            aswitchService.removeFlow(aswFlowForward);
            aswitchService.removeFlow(aswFlowReverse);
            changedIsls.add(islToRemove);
        });
    }

    /**
     * Restores rules on intermediate switch for given ISLs. This reverts the actions done by {@link #transitIslDown()}
     */
    @When("Changed ISLs? go(?:es)? up")
    public void transitIslUp() {
        changedIsls.forEach(isl -> {
            TopologyDefinition.ASwitch aswitch = isl.getAswitch();
            ASwitchFlow aswFlowForward = new ASwitchFlow(aswitch.getInPort(), aswitch.getOutPort());
            ASwitchFlow aswFlowReverse = new ASwitchFlow(aswitch.getOutPort(), aswitch.getInPort());
            aswitchService.addFlow(aswFlowForward);
            aswitchService.addFlow(aswFlowReverse);
        });
    }

    /**
     * This method waits for default amount of retries before the ISL status has the desired state.
     * Throws assertion error otherwise. Verifications are done via Northbound.
     *
     * @param islStatus required ISL status
     */
    @Then("ISLs? status changes? to (.*)")
    public void waitForIslStatus(String islStatus) {
        IslChangeType expectedIslState = IslChangeType.valueOf(islStatus);
        changedIsls.forEach(isl -> {
            IslChangeType actualIslState = Failsafe.with(retryPolicy
                    .retryIf(state -> state != expectedIslState))
                    .get(() -> northboundService.getAllLinks().stream().filter(link -> {
                        PathNode src = link.getPath().get(0);
                        PathNode dst = link.getPath().get(1);
                        return src.getPortNo() == isl.getSrcPort() && dst.getPortNo() == isl.getDstPort();
                    }).findFirst().get().getState());
            assertEquals(expectedIslState, actualIslState);
        });
    }

    @After({"@requires_cleanup", "@cuts_out_isls"})
    public void bringIslBack() {
        transitIslUp();
    }
}
