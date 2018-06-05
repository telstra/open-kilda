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
import static org.junit.Assert.assertTrue;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Isl;
import org.openkilda.atdd.staging.service.aswitch.ASwitchService;
import org.openkilda.atdd.staging.service.aswitch.model.ASwitchFlow;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.steps.helpers.TopologyUnderTest;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IslSteps {

    private static final Logger LOGGER = LoggerFactory.getLogger(IslSteps.class);

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private ASwitchService aswitchService;

    @Autowired
    private TopologyUnderTest topologyUnderTest;

    @Autowired
    private TopologyDefinition topologyDefinition;

    List<TopologyDefinition.Isl> changedIsls = new ArrayList<>();

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
            aswitchService.removeFlow(Arrays.asList(aswFlowForward, aswFlowReverse));
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
            aswitchService.addFlow(Arrays.asList(aswFlowForward, aswFlowReverse));
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
            IslChangeType actualIslState = Failsafe.with(retryPolicy()
                    .retryIf(state -> state != expectedIslState))
                    .get(() -> northboundService.getAllLinks().stream().filter(link -> {
                        PathNode src = link.getPath().get(0);
                        PathNode dst = link.getPath().get(1);
                        return src.getPortNo() == isl.getSrcPort() && dst.getPortNo() == isl.getDstPort();
                    }).findFirst().get().getState());
            assertEquals(expectedIslState, actualIslState);
        });
    }

    @Then("ISLs? status is (.*)")
    public void checkIslStatus(String islStatus) {
        IslChangeType expectedIslState = IslChangeType.valueOf(islStatus);
        changedIsls.forEach(isl -> {
            IslChangeType actualIslState = northboundService.getAllLinks().stream().filter(link -> {
                PathNode src = link.getPath().get(0);
                PathNode dst = link.getPath().get(1);
                return src.getPortNo() == isl.getSrcPort() && dst.getPortNo() == isl.getDstPort();
            }).findFirst().get().getState();
            assertEquals(expectedIslState, actualIslState);
        });
    }

    @When("^request all available links from Northbound$")
    public void requestAllAvailableLinksFromNorthbound() {
        topologyUnderTest.setResponse(northboundService.getAllLinks());
    }

    @Then("^response has at least (\\d+) links?$")
    public void responseHasAtLeastLink(int linksAmount) {
        List<IslInfoData> response = (List<IslInfoData>) topologyUnderTest.getResponse();
        assertTrue(response.size() >= linksAmount);
    }

    private RetryPolicy retryPolicy() {
        return new RetryPolicy()
                .withDelay(2, TimeUnit.SECONDS)
                .withMaxRetries(10);
    }

    @Given("^select a random isl and alias it as '(.*)'$")
    public void selectARandomIslAndAliasItAsIsl(String islAlias) {
        List<Isl> isls = getUnaliasedIsls();
        Random r = new Random();
        Isl theIsl = isls.get(r.nextInt(isls.size()));
        LOGGER.info("Selected random isl: {}", theIsl.toString());
        topologyUnderTest.getAliasedObjects().put(islAlias, theIsl);
    }

    private List<Isl> getUnaliasedIsls() {
        List<Isl> aliasedIsls = topologyUnderTest.getAliasedObjects().values().stream()
                .filter(obj -> obj instanceof Isl)
                .map(sw -> (Isl) sw).collect(Collectors.toList());
        List<Isl> isls = (List<Isl>) CollectionUtils.subtract(
                topologyDefinition.getIslsForActiveSwitches(), aliasedIsls);
        Assume.assumeTrue("No unaliased isls left, unable to proceed", !isls.isEmpty());
        return isls;
    }
}
