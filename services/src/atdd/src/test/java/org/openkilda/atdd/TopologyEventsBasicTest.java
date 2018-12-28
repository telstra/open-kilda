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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.LinksUtils;
import org.openkilda.SwitchesUtils;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.northbound.dto.links.LinkStatus;
import org.openkilda.northbound.dto.links.PathDto;
import org.openkilda.northbound.dto.switches.SwitchDto;
import org.openkilda.topo.builders.TestTopologyBuilder;

import cucumber.api.PendingException;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.junit.Before;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Created by carmine on 5/1/17.
 */
public class TopologyEventsBasicTest {

    private LinkDto manipulatedLink;

    @Before
    public void setUp() {
        manipulatedLink = null;
    }

    @When("^multiple links exist between all switches$")
    public void multipleLinksExistBetweenAllSwitches() throws Exception {
        List<String> switchIds = IntStream.range(1, 6)
                .mapToObj(TestTopologyBuilder::intToSwitchId)
                .collect(Collectors.toList());
        assertTrue("Switches should have multiple links",
                getSwitchesWithoutMultipleLinks(switchIds).isEmpty());
    }

    private List<String> getSwitchesWithoutMultipleLinks(List<String> switches) {
        List<LinkDto> links = LinksUtils.dumpLinks();

        return switches.stream()
                .filter(sw -> isSwitchHasLessThanTwoLinks(sw, links))
                .collect(Collectors.toList());
    }

    @When("^a link is dropped in the middle$")
    public void linkIsDroppedInTheMiddle() {
        List<LinkDto> links = LinksUtils.dumpLinks();
        manipulatedLink = getMiddleLink(links);

        PathDto node = manipulatedLink.getPath().get(0);
        assertTrue(LinksUtils.islFail(getSwitchName(node.getSwitchId()), String.valueOf(node.getPortNo())));
    }

    @Then("^the link will have no health checks$")
    public void the_link_will_have_no_health_checks() {
        List<LinkDto> links = LinksUtils.dumpLinks();
        List<LinkDto> cutLinks = links.stream()
                .filter(isl -> isl.getState() != LinkStatus.DISCOVERED)
                .collect(Collectors.toList());

        assertFalse("Link should be cut", cutLinks.isEmpty());
        assertThat("Only one link should be cut", cutLinks.size(), is(1));
    }

    @Then("^the link disappears from the topology engine in (\\d+) seconds\\.$")
    public void theLinkDisappearsFromTheTopologyEngine(int timeout) {
        List<LinkDto> cutLinks = Failsafe.with(new RetryPolicy()
                .withDelay(2, TimeUnit.SECONDS)
                .withMaxDuration(timeout, TimeUnit.SECONDS)
                .retryIf(links -> links instanceof List && ((List) links).isEmpty()))
                .get(() -> LinksUtils.dumpLinks().stream()
                        .filter(isl -> isl.getState() != LinkStatus.DISCOVERED)
                        .collect(Collectors.toList())
                );

        assertFalse("Link should be cut", cutLinks.isEmpty());
        assertThat("Only one link should be cut", cutLinks,
                hasItems(hasProperty("path", equalTo(manipulatedLink.getPath()))));
    }

    @When("^a link is added in the middle$")
    public void linkIsAddedInTheMiddle() throws Exception {
        List<LinkDto> links = LinksUtils.dumpLinks();
        LinkDto middleLink = getMiddleLink(links);

        String srcSwitch = getSwitchName(middleLink.getPath().get(0).getSwitchId());
        String dstSwitch = getSwitchName(middleLink.getPath().get(1).getSwitchId());
        assertTrue("Link is not added", LinksUtils.addLink(srcSwitch, dstSwitch));
        TimeUnit.SECONDS.sleep(2);
    }

    @Then("^the link will have health checks$")
    public void the_link_will_have_health_checks() {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the link appears in the topology engine\\.$")
    public void theLinkAppearsInTheTopologyEngine() {
        List<LinkDto> links = LinksUtils.dumpLinks();
        assertThat("Amount of links should be 18 (initial 16 and 2 newly created)", links.size(), is(18));
    }

    @When("^a switch is dropped in the middle$")
    public void switchIsDroppedInTheMiddle() throws Exception {
        List<SwitchDto> switches = SwitchesUtils.dumpSwitches();
        SwitchDto middleSwitch = getMiddleSwitch(switches);
        assertTrue("Should successfully knockout switch",
                SwitchesUtils.knockoutSwitch(getSwitchName(middleSwitch.getSwitchId())));

        TimeUnit.SECONDS.sleep(1);
        List<SwitchDto> updatedSwitches = SwitchesUtils.dumpSwitches();
        SwitchDto deactivatedSwitch = updatedSwitches.stream()
                .filter(sw -> sw.getSwitchId().equals(middleSwitch.getSwitchId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Switch should exist"));
        assertThat(deactivatedSwitch.getState(), is(SwitchChangeType.DEACTIVATED));
    }

    @Then("^all links through the dropped switch will have no health checks$")
    public void allLinksThroughTheDroppedSwitchWillHaveNoHealthChecks() {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^the links disappear from the topology engine\\.$")
    public void theLinksDisappearFromTheTopologyEngine() throws Exception {
        //todo check whether we need to wait until links will disappear or
        // we might delete them instantly when switch goes down
        TimeUnit.SECONDS.sleep(15);
        final SwitchDto middleSwitch = getMiddleSwitch(SwitchesUtils.dumpSwitches());
        final List<LinkDto> links = LinksUtils.dumpLinks();

        List<LinkDto> switchLinks = links.stream()
                .filter(isl -> isLinkBelongToSwitch(middleSwitch.getSwitchId(), isl))
                .filter(isl -> isl.getState() == LinkStatus.DISCOVERED)
                .collect(Collectors.toList());
        assertTrue("Switch shouldn't have any active links", switchLinks.isEmpty());
    }

    @Then("^the switch disappears from the topology engine\\.$")
    public void theSwitchDisappearsFromTheTopologyEngine() {
        List<SwitchDto> switches = SwitchesUtils.dumpSwitches();
        SwitchDto middleSwitch = getMiddleSwitch(switches);

        //right now switch doesn't disappear in neo4j - we just update status
        assertThat(middleSwitch.getState(), is(SwitchChangeType.DEACTIVATED));
    }

    @When("^a switch is added at the edge$")
    public void switchIsAddedAtTheEdge() throws Exception {
        assertTrue("Should add switch to mininet topology",
                SwitchesUtils.addSwitch("01010001", "DEADBEEF01010001"));
        TimeUnit.SECONDS.sleep(1);
    }

    @When("^links are added between the new switch and its neighbor$")
    public void linksAreAddedBetweenTheNewSwitchAndItsNeighbor() throws Exception {
        List<LinkDto> links = LinksUtils.dumpLinks();
        List<SwitchDto> switches = SwitchesUtils.dumpSwitches();

        SwitchDto switchWithoutLinks = switches.stream()
                .filter(sw -> links.stream()
                        .anyMatch(isl -> isLinkBelongToSwitch(sw.getSwitchId(), isl)))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("At least one switch should exist"));

        SwitchDto latestConnectedSwitch = switches.stream()
                .sorted(Comparator.comparing(SwitchDto::getSwitchId).reversed())
                .findFirst().get();

        assertTrue(LinksUtils.addLink(getSwitchName(switchWithoutLinks.getSwitchId()),
                getSwitchName(latestConnectedSwitch.getSwitchId())));
        assertTrue(LinksUtils.addLink(getSwitchName(switchWithoutLinks.getSwitchId()),
                getSwitchName(latestConnectedSwitch.getSwitchId())));
        TimeUnit.SECONDS.sleep(1);
    }

    @Then("^all links through the added switch will have health checks$")
    public void allLinksThroughTheAddedSwitchWillHaveHealthChecks() {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @Then("^now amount of switches is (\\d+)\\.$")
    public void theSwitchAppearsInTheTopologyEngine(int switches) {
        List<SwitchDto> switchList = SwitchesUtils.dumpSwitches();
        List<SwitchDto> activeSwitches = switchList.stream()
                .filter(sw -> SwitchChangeType.ACTIVATED.getType().equals(sw.getState()))
                .collect(Collectors.toList());

        assertThat("Switch should disappear from neo4j", activeSwitches.size(), is(switches));
    }

    private boolean isSwitchHasLessThanTwoLinks(String switchId, List<LinkDto> links) {
        int inputsAmount = 0;
        int outputsAmount = 0;
        for (LinkDto isl : links) {
            for (PathDto node : isl.getPath()) {
                if (switchId.equalsIgnoreCase(node.getSwitchId())) {
                    if (node.getSeqId() == 0) {
                        outputsAmount++;
                    } else if (node.getSeqId() == 1) {
                        inputsAmount++;
                    }
                }
            }
        }

        //check whether switch has more than one link in both direction (sequence id 0 and 1)
        return inputsAmount <= NumberUtils.INTEGER_ONE && outputsAmount <= NumberUtils.INTEGER_ONE;
    }

    private LinkDto getMiddleLink(List<LinkDto> links) {
        return links.stream()
                .sorted(Comparator.comparing((isl) -> isl.getPath().get(0).getSwitchId()))
                .collect(Collectors.toList())
                .get((links.size() / 2) + 1);
    }

    private SwitchDto getMiddleSwitch(List<SwitchDto> switches) {
        return switches.stream()
                .sorted(Comparator.comparing(SwitchDto::getSwitchId))
                .collect(Collectors.toList())
                .get(switches.size() / 2);
    }

    private String getSwitchName(String switchId) {
        return switchId.replaceAll("(\\A.*?:.*?:.*?:.*?:)|[:]", StringUtils.EMPTY);
    }

    private boolean isLinkBelongToSwitch(String switchId, LinkDto isl) {
        return switchId.equalsIgnoreCase(isl.getPath().get(0).getSwitchId())
                || switchId.equalsIgnoreCase(isl.getPath().get(1).getSwitchId());
    }
}
