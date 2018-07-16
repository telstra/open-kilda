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

package org.openkilda.wfm.isl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.NetworkEndpoint;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;


/**
 * The DiscoveryManager is the main class that governs ISL discovery. It develops a list of
 * switch/ports to send discovery/health checks on, and a list of failures to notify others about.
 *
 * <p>OFELinkBolt is the primary user of this class, leveraging DiscoveryManager.Plan
 *
 * <p>The primary test scenarios that are of interest..
 */
public class DiscoveryManagerTest {

    private DiscoveryManager dm;
    private NetworkEndpoint srcNode1;
    private NetworkEndpoint dstNode1;
    private NetworkEndpoint srcNode2;
    private NetworkEndpoint dstNode2;
    private NetworkEndpoint srcNode3;
    private NetworkEndpoint dstNode3;
    private int islHealthCheckInterval;
    private int islHealthFailureLimit;
    // Determines how many attempts to discover isl will be made
    private int maxAttemptsLimit;
    private int minutesKeepRemovedIsl;

    /**
     * Init method.
     */
    @Before
    public void setUp() throws Exception {
        islHealthCheckInterval = 0; // means check ever tick
        islHealthFailureLimit  = 1; // for testing, failure after 1 tick;
        maxAttemptsLimit = 2;
        minutesKeepRemovedIsl = 10;

        dm = new DiscoveryManager(
                new DummyIIslFilter(), new LinkedList<>(), islHealthCheckInterval,
                islHealthFailureLimit, maxAttemptsLimit, minutesKeepRemovedIsl);
    }

    /**
     * Creates three endpoints(switch and port) and activates such ports.
     */
    private void setupThreeLinks() {
        srcNode1 = new NetworkEndpoint("sw1", 1);
        dstNode1 = new NetworkEndpoint("sw3", 1);
        srcNode2 = new NetworkEndpoint("sw1", 2);
        dstNode2 = new NetworkEndpoint("sw3", 2);
        srcNode3 = new NetworkEndpoint("sw2", 1);
        dstNode3 = new NetworkEndpoint("sw3", 3);

        dm.handlePortUp(srcNode1.getDatapath(), srcNode1.getPortNumber());
        dm.handlePortUp(srcNode2.getDatapath(), srcNode2.getPortNumber());
        dm.handlePortUp(srcNode3.getDatapath(), srcNode3.getPortNumber());
    }

    @Test
    public void shouldDiscoveryPlanContainsAllEndpoints() {
        setupThreeLinks();

        // Initially, given 0 tick interval, everything should be in discoveryPlan and no failures
        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());
    }

    @Test
    public void shouldAlwaysSendDiscoveryForActiveLinks() {
        setupThreeLinks();

        for (int attempt = 0; attempt <= maxAttemptsLimit + islHealthFailureLimit + 1; attempt++) {

            DiscoveryManager.Plan discoveryPlan;
            discoveryPlan = dm.makeDiscoveryPlan();
            // number of attempts is always cleared up after receiving the response
            assertEquals(1, dm.findBySourceSwitch(srcNode1).get(0).getAttempts());
            assertEquals(1, dm.findBySourceSwitch(srcNode2).get(0).getAttempts());
            assertEquals(1, dm.findBySourceSwitch(srcNode3).get(0).getAttempts());

            discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));
            // we should receive acknowledge for every sent disco packet
            assertEquals(1, dm.findBySourceSwitch(srcNode1).get(0).getAckAttempts());
            assertEquals(1, dm.findBySourceSwitch(srcNode2).get(0).getAckAttempts());
            assertEquals(1, dm.findBySourceSwitch(srcNode3).get(0).getAckAttempts());

            assertEquals(3, discoveryPlan.needDiscovery.size());
            assertEquals(0, discoveryPlan.discoveryFailure.size());

            dm.handleDiscovered(srcNode1.getDatapath(), srcNode1.getPortNumber(),
                    dstNode1.getDatapath(), dstNode1.getPortNumber());
            dm.handleDiscovered(srcNode2.getDatapath(), srcNode2.getPortNumber(),
                    dstNode2.getDatapath(), dstNode2.getPortNumber());
            dm.handleDiscovered(srcNode3.getDatapath(), srcNode3.getPortNumber(),
                    dstNode3.getDatapath(), dstNode3.getPortNumber());

            verifyAllLinks(true);
        }
    }

    @Test
    public void shouldBreakDiscoveredLinkCorrectly() {
        DiscoveryLink link = new DiscoveryLink("sw1", 1, "sw2", 2,
                islHealthCheckInterval, islHealthFailureLimit, false);
        NetworkEndpoint srcNode = link.getSource();
        dm.handlePortUp(srcNode.getDatapath(), srcNode.getPortNumber());

        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));
        dm.handleDiscovered(srcNode.getDatapath(), srcNode.getPortNumber(),
                link.getDestination().getDatapath(), link.getDestination().getPortNumber());

        assertTrue(dm.findBySourceSwitch(srcNode).get(0).isActive());

        // 1st attempt
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(1, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());
        discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));
        assertTrue(dm.findBySourceSwitch(srcNode).get(0).isActive());

        // 2nd attempt and we have only one acknowledged dispatch of disco packet
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(1, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());
        discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));
        assertTrue(dm.findBySourceSwitch(srcNode).get(0).isActive());

        // 3rd attempt and 1st failure
        // link should be marked as inactive because 2 ackAttempts > current islHealthFailureLimit
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(1, discoveryPlan.needDiscovery.size());
        assertEquals(1, discoveryPlan.discoveryFailure.size());
        discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));
        assertFalse(dm.findBySourceSwitch(srcNode).get(0).isActive());

        // 4th attempt, 1st consecutive failure
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(1, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());
        discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));
        assertFalse(dm.findBySourceSwitch(srcNode).get(0).isActive());

        // 5th attempt, 2nd consecutive failure
        // should be removed from discovery because consecutiveFailure > consecutiveFailureLimit
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(0, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());
        assertFalse(dm.findBySourceSwitch(srcNode).get(0).isActive());
    }

    @Test
    public void shouldIslBeAbandonedAfterSeveralConsecutiveFailures() {
        setupThreeLinks();

        DiscoveryManager.Plan discoveryPlan;
        for (int attempt = 0; attempt <= maxAttemptsLimit + islHealthFailureLimit; attempt++) {
            discoveryPlan = dm.makeDiscoveryPlan();
            discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));
            assertEquals(3, discoveryPlan.needDiscovery.size());
            assertEquals(0, discoveryPlan.discoveryFailure.size());
        }

        // 5th attempt to send disco packet. 3rd failure and it is bigger than islConsecutiveFailureLimit (2).
        discoveryPlan = dm.makeDiscoveryPlan();
        // we should stop sending disco packets from these endpoints.
        assertEquals(0, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // all links are discovered - we need to add them to discovery plan again.
        dm.handleDiscovered(srcNode1.getDatapath(), srcNode1.getPortNumber(),
                dstNode1.getDatapath(), dstNode1.getPortNumber());
        dm.handleDiscovered(srcNode2.getDatapath(),
                srcNode2.getPortNumber(), dstNode2.getDatapath(), dstNode2.getPortNumber());
        dm.handleDiscovered(srcNode3.getDatapath(), srcNode3.getPortNumber(),
                dstNode3.getDatapath(), dstNode3.getPortNumber());
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());
    }

    @Test
    public void shouldNotStopSendingDiscoIfConfirmationOfSendingDiscoIsNotReceived() {
        setupThreeLinks();

        // if we don't receive confirmation of sending discovery packet we should not mark ISL as inactive
        for (int attempt = 0; attempt <= maxAttemptsLimit + islHealthFailureLimit + 1; attempt++) {
            DiscoveryManager.Plan discoveryPlan;
            discoveryPlan = dm.makeDiscoveryPlan();
            assertEquals(3, discoveryPlan.needDiscovery.size());
            assertEquals(0, discoveryPlan.discoveryFailure.size());
        }
    }

    @Test
    public void shouldNotBreakActiveIslIfConfirmationOfSendingDiscoIsNotReceived() {
        setupThreeLinks();
        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        discoveryPlan.needDiscovery.forEach(endpoint -> dm.handleDiscoPacketSent(endpoint));

        dm.handleDiscovered(srcNode1.getDatapath(), srcNode1.getPortNumber(),
                dstNode1.getDatapath(), dstNode1.getPortNumber());
        dm.handleDiscovered(srcNode2.getDatapath(), srcNode2.getPortNumber(),
                dstNode2.getDatapath(), dstNode2.getPortNumber());
        dm.handleDiscovered(srcNode3.getDatapath(), srcNode3.getPortNumber(),
                dstNode3.getDatapath(), dstNode3.getPortNumber());

        // if we don't receive confirmation of sending discovery packet we should not mark ISL as inactive
        for (int attempt = 0; attempt <= maxAttemptsLimit + islHealthFailureLimit + 1; attempt++) {
            discoveryPlan = dm.makeDiscoveryPlan();
            assertEquals(3, discoveryPlan.needDiscovery.size());
            assertEquals(0, discoveryPlan.discoveryFailure.size());
        }

        verifyAllLinks(true);
    }

    @Test
    public void shouldCheckIslWithInterval() {
        // verify Health Check Interval is working properly.
        islHealthCheckInterval = 2;
        islHealthFailureLimit  = 4; // for testing, failure after 1 tick;
        maxAttemptsLimit = 8;

        dm = new DiscoveryManager(
                new DummyIIslFilter(), new LinkedList<>(), islHealthCheckInterval,
                islHealthFailureLimit, maxAttemptsLimit, minutesKeepRemovedIsl);
        setupThreeLinks();
        // Initially, given 2 tick interval, nothing should be in the lists
        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(0, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Still nothing
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(0, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Now we discover
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // But not now
        discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(0, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());
    }

    @Test
    public void handleDiscovered() {
        // Test whether it handle the state change properly .. ie is this a new failure ore not.
        // - if !discovered, then we've just found an ISL
        // - if discovered, but found failures (and now we have success), that is a change
        // - state information .. consecutive failures is zero, tick/attempts is zero, success++

        setupThreeLinks();
        NetworkEndpoint dstNode = dstNode2;

        // An initial success is state change
        assertEquals(true, dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode.getSwitchDpId(), dstNode.getPortId()));
        // A repeated success is not
        assertEquals(false, dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode.getSwitchDpId(), dstNode.getPortId()));

        // Let it fail, then succeed .. both are state changes
        assertEquals(true, dm.handleFailed(srcNode2.getSwitchDpId(), srcNode2.getPortId()));
        assertEquals(true, dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode.getSwitchDpId(), dstNode.getPortId()));
        assertEquals(false, dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode.getSwitchDpId(), dstNode.getPortId()));
    }

    @Test
    public void handleFailed() {
        // Test whether it handle the state change properly .. ie is this a new failure or not.
        // NB: handleFailed is called when an ISL failure is received from FL; the other kind of
        //      failure is if there is no response, and that is handled by makeDiscoveryPlan().
        List<DiscoveryLink> nodes;
        setupThreeLinks();

        // After a PortUP, a failure isn't a state change, the default is to assume it isn't an ISL
        assertEquals(false, dm.handleFailed(srcNode2.getSwitchDpId(), srcNode2.getPortId()));
        // A success after a failure is state change
        assertEquals(true, dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId()));
        // A failure after a success is state change
        assertEquals(true, dm.handleFailed(srcNode2.getSwitchDpId(), srcNode2.getPortId()));
        // Repeated failures isn't a state change.
        assertEquals(false, dm.handleFailed(srcNode2.getSwitchDpId(), srcNode2.getPortId()));
    }

    @Test
    public void handleSwitchUp() {
        // Verify that all switch/ports on this switch of the ISL flag cleared

        // Generally speaking, nothing interesting happens on a SwitchUp, since all of the action is
        // in PortUp.  However, this one area, when pre-existing ISLs are already in the DM, should
        // be tested. We need to create some ISLs, then send the SwitchUp, and confirm isFoundIsl
        // is cleared.
        setupThreeLinks();

        // discover them and confirm all discovered
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId());
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId());
        dm.handleDiscovered(srcNode3.getSwitchDpId(), srcNode3.getPortId(),
                dstNode3.getSwitchDpId(), dstNode3.getPortId());

        List<DiscoveryLink> nodes;
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode1.getSwitchDpId(), 0));
        assertEquals(true, nodes.get(0).isActive());
        assertEquals(true, nodes.get(1).isActive());
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode3.getSwitchDpId(), 0));
        assertEquals(true, nodes.get(0).isActive());

        // now send SwitchUp and confirm sw1 all go back to not found, sw2 unchanged
        dm.handleSwitchUp(srcNode1.getSwitchDpId());
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode1.getSwitchDpId(), 0));
        assertEquals(false, nodes.get(0).isActive());
        assertEquals(false, nodes.get(1).isActive());
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode3.getSwitchDpId(), 0));
        assertEquals(true, nodes.get(0).isActive());

        // now confirm they go back to found upon next Discovery.
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId());
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId());
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode1.getSwitchDpId(), 0));
        assertEquals(true, nodes.get(0).isActive());
        assertEquals(true, nodes.get(1).isActive());
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode3.getSwitchDpId(), 0));
        assertEquals(true, nodes.get(0).isActive());
    }

    @Test
    public void handleSwitchDown() {
        // verify all the ISL switch/ports are deleted
        List<DiscoveryLink> nodes;
        setupThreeLinks();

        // 3 nodes - 2 in sw1, one in sw2; verify dropping sw1 drops 2 nodes (1 remaining)
        nodes = dm.findBySourceSwitch(srcNode1.getSwitchDpId());
        assertEquals(2, nodes.size());
        nodes = dm.findBySourceSwitch(srcNode3.getSwitchDpId());
        assertEquals(1, nodes.size());

        // Drop the switch, and then the same 4 lines of code, except 0 size for sw1 nodes.
        dm.handleSwitchDown(srcNode1.getSwitchDpId());
        nodes = dm.findBySourceSwitch(srcNode1.getSwitchDpId());
        assertEquals(0, nodes.size());
        nodes = dm.findBySourceSwitch((srcNode3.getSwitchDpId()));
        assertEquals(1, nodes.size());
    }

    @Test
    public void handlePortUp() {
        // verify the switch/port is added
        // verify that adding an existing one doesn't crash it.
        List<DiscoveryLink> links;

        // Put in 1 node and verify it is there.
        DiscoveryLink link = new DiscoveryLink("sw1", 1, islHealthCheckInterval, islHealthFailureLimit);
        NetworkEndpoint srcNode = link.getSource();
        dm.handlePortUp(srcNode.getSwitchDpId(), srcNode.getPortId());
        links = dm.findBySourceSwitch(new NetworkEndpoint(srcNode.getSwitchDpId(), srcNode.getPortId()));
        assertEquals(1, links.size());
        assertEquals(link, links.get(0));

        // try to add it back in .. should still only be 1
        dm.handlePortUp(srcNode.getSwitchDpId(), srcNode.getPortId());
        links = dm.findBySourceSwitch(new NetworkEndpoint(srcNode.getSwitchDpId(), srcNode.getPortId()));
        assertEquals(1, links.size());
        assertEquals(link, links.get(0));
    }

    @Test
    public void handlePortDown() {
        // verify the switch/port is deleted.
        // verify remove one that doesn't exist doesn't crash it
        List<DiscoveryLink> nodes;

        // Put in 1 node and then remove it. The handlePortUp test ensures the Port Up works.
        DiscoveryLink link = new DiscoveryLink("sw1", 1, islHealthCheckInterval, islHealthFailureLimit);
        NetworkEndpoint srcNode = link.getSource();
        dm.handlePortUp(srcNode.getSwitchDpId(), srcNode.getPortId());
        dm.handlePortDown(srcNode.getSwitchDpId(), srcNode.getPortId());
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode.getSwitchDpId(), srcNode.getPortId()));
        assertEquals(0, nodes.size());

        // call PortDown again .. verify nothing bad happens.
        dm.handlePortDown(srcNode.getSwitchDpId(), srcNode.getPortId());
        nodes = dm.findBySourceSwitch(new NetworkEndpoint(srcNode.getSwitchDpId(), srcNode.getPortId()));
        assertEquals(0, nodes.size());
    }

    @Test
    public void shouldWorksCorrectlyWhenIslUpdating() {
        setupThreeLinks();

        boolean stateChanged = dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId());
        assertTrue(stateChanged);

        stateChanged = dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId());
        assertFalse(stateChanged);

        stateChanged = dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId());
        assertTrue(stateChanged);
    }

    @Test
    public void shouldReturnFalseWhenDiscoPacketsAreNotSendingFromEndpoint() {
        NetworkEndpoint source = new NetworkEndpoint("sw1", 1);
        assertFalse(dm.isInDiscoveryPlan(source.getDatapath(), source.getPortNumber()));
    }

    @Test
    public void shouldEndpointBeRemovedFromDiscoveryPlanAfterFailures() {
        NetworkEndpoint source = new NetworkEndpoint("sw1", 1);
        dm.handlePortUp(source.getDatapath(), source.getPortNumber());

        // 1st attempt to discover
        dm.makeDiscoveryPlan();
        dm.handleDiscoPacketSent(source);

        // 2nd attempt to discover
        dm.makeDiscoveryPlan();
        dm.handleDiscoPacketSent(source);

        // 3rd attempt to send disco packet. 1st failure because ISL still not discovered.
        dm.makeDiscoveryPlan();
        dm.handleDiscoPacketSent(source);

        // 4th attempt to send disco packet. 2nd failure because ISL still not discovered.
        dm.makeDiscoveryPlan();

        // 5th attempt to send disco packet. 3rd failure and it is bigger than islConsecutiveFailureLimit (2).
        dm.makeDiscoveryPlan();

        assertFalse(dm.isInDiscoveryPlan(source.getDatapath(), source.getPortNumber()));
    }

    @Test
    public void shouldReturnTrueWhenEndpointIsSendingDisco() {
        NetworkEndpoint source = new NetworkEndpoint("sw1", 1);
        dm.handlePortUp(source.getDatapath(), source.getPortNumber());
        assertTrue(dm.isInDiscoveryPlan(source.getDatapath(), source.getPortNumber()));
    }

    @Test
    public void shouldIncreaseAcknowledgedAttempts() {
        NetworkEndpoint source = new NetworkEndpoint("sw1", 1);
        dm.handlePortUp(source.getDatapath(), source.getPortNumber());

        // originally all counters should be 0.
        List<DiscoveryLink> links = dm.findBySourceSwitch(source);
        assertEquals(1, links.size());
        assertEquals(0, links.get(0).getAckAttempts());
        assertEquals(0, links.get(0).getAttempts());

        // simulate receiving the confirmation abound sending disco packet
        dm.handleDiscoPacketSent(source);
        links = dm.findBySourceSwitch(source);
        assertEquals(1, links.size());
        assertEquals(1, links.get(0).getAckAttempts());
        assertEquals(0, links.get(0).getAttempts());
    }

    private void verifyAllLinks() {
        assertTrue(dm.findBySourceSwitch(srcNode1).get(0).isActive());
        assertTrue(dm.findBySourceSwitch(srcNode2).get(0).isActive());
        assertTrue(dm.findBySourceSwitch(srcNode3).get(0).isActive());
    }

    @Test
    public void shouldNotDeactivateLinkOnPortRegistration() {
        // given
        setupThreeLinks();

        List<DiscoveryLink> foundAsLink1Before = dm.findBySourceSwitch(srcNode1);
        assertEquals(1, foundAsLink1Before.size());
        foundAsLink1Before.get(0).activate(dstNode1);

        // when
        DiscoveryLink affectedLink = dm.registerPort(srcNode1.getDatapath(), srcNode1.getPortNumber());
        assertEquals(affectedLink.getSource(), srcNode1);
        assertTrue("The link must be active.", affectedLink.isActive());

        // then
        List<DiscoveryLink> foundAsLink1After = dm.findBySourceSwitch(srcNode1);
        assertEquals(1, foundAsLink1After.size());
        assertTrue("The link must be active.", foundAsLink1After.get(0).isActive());
    }

    @Test
    public void shouldAddInactiveLinkOnPortRegistration() {
        // given
        setupThreeLinks();

        NetworkEndpoint srcNode4 = new NetworkEndpoint("sw2", 2);

        List<DiscoveryLink> foundAsLink4Before = dm.findBySourceSwitch(srcNode4);
        assertTrue(foundAsLink4Before.isEmpty());

        // when
        DiscoveryLink addedLink = dm.registerPort(srcNode4.getDatapath(), srcNode4.getPortNumber());
        assertEquals(addedLink.getSource(), srcNode4);
        assertFalse("The link must be inactive.", addedLink.isActive());

        // then
        List<DiscoveryLink> foundAsLink4After = dm.findBySourceSwitch(srcNode4);
        assertEquals(1, foundAsLink4After.size());
    }
}
