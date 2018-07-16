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
    private DiscoveryLink link1;
    private DiscoveryLink link2;
    private DiscoveryLink link3;
    private NetworkEndpoint srcNode1;
    private NetworkEndpoint dstNode1;
    private NetworkEndpoint srcNode2;
    private NetworkEndpoint dstNode2;
    private NetworkEndpoint srcNode3;
    private NetworkEndpoint dstNode3;
    private int islHealthCheckInterval;
    private int islHealthFailureLimit;
    private int forlornLimit;
    private int minutesKeepRemovedIsl;

    /**
     * Init method.
     */
    @Before
    public void setUp() throws Exception {
        islHealthCheckInterval = 0; // means check ever tick
        islHealthFailureLimit  = 1; // for testing, failure after 1 tick;
        forlornLimit = 2;
        forlornLimit = 2;
        minutesKeepRemovedIsl = 10;

        dm = new DiscoveryManager(
                new DummyIIslFilter(), new LinkedList<>(), islHealthCheckInterval,
                islHealthFailureLimit, forlornLimit, minutesKeepRemovedIsl);
    }

    /**
     * Several tests start with creating/adding 3 ports.
     * */
    void setupThreeLinks() {
        link1 = new DiscoveryLink("sw1", 1, "sw3", 1, islHealthCheckInterval, forlornLimit, true);
        link2 = new DiscoveryLink("sw1", 2, "sw3", 2, islHealthCheckInterval, forlornLimit, true);
        link3 = new DiscoveryLink("sw2", 1, "sw3", 3, islHealthCheckInterval, forlornLimit, true);
        
        srcNode1 = link1.getSource();
        dstNode1 = link1.getDestination();
        srcNode2 = link2.getSource();
        dstNode2 = link2.getDestination();
        srcNode3 = link3.getSource();
        dstNode3 = link3.getDestination();
        dm.handlePortUp(srcNode1.getSwitchDpId(), srcNode1.getPortId());
        dm.handlePortUp(srcNode2.getSwitchDpId(), srcNode2.getPortId());
        dm.handlePortUp(srcNode3.getSwitchDpId(), srcNode3.getPortId());
    }


    @Test
    public void makeDiscoveryPlan() {
        // The discovery plan should test every link that isn't forlorned and where sufficient ticks
        // have passed. Additionally, the disco plan determines what is part of the failure notification
        // plan.
        //
        // Tests:
        //  1) all ports that should send a disco packet sent are in the needDiscovery list.
        //  2) all ports that haven't had a response within timeout are in the discoveryFailure list.
        //
        List<DiscoveryLink> nodes;
        setupThreeLinks();

        // Initially, given 0 tick interval, everything should be in discoveryPlan and no failures
        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // In order for one of them to be in failure plan, it has to have been a success beforehand
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode3.getSwitchDpId(), srcNode3.getPortId(),
                dstNode3.getSwitchDpId(), dstNode3.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // increase attempts of all to 1
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // attempts: link1&2 @ 1, link3 @ 2
        discoveryPlan = dm.makeDiscoveryPlan();                      // the attempts test is based on the 1 & 2 count
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(1, discoveryPlan.discoveryFailure.size());
        assertEquals(srcNode3.getSwitchDpId(), discoveryPlan.discoveryFailure.get(0).getSwitchDpId());
        assertEquals(srcNode3.getPortId(), discoveryPlan.discoveryFailure.get(0).getPortId());

        // Now verify it doesn't re-send the failure
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId()); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                      // the attempts test is based on the 0 & 3 count
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Now test forlorn
        dm.makeDiscoveryPlan();                                       // the attempts test is based on the 1 & 4 count
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(), 
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode2.getSwitchDpId(),
                srcNode2.getPortId(), dstNode2.getSwitchDpId(), 0); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                       // now consecutive failure at 3 for link3
        assertEquals(2, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());


        // verify Health Check Interval is working properly.
        islHealthCheckInterval = 2;
        islHealthFailureLimit  = 4; // for testing, failure after 1 tick;
        forlornLimit = 8;

        dm = new DiscoveryManager(
                new DummyIIslFilter(), new LinkedList<>(), islHealthCheckInterval,
                islHealthFailureLimit, forlornLimit, minutesKeepRemovedIsl);
        setupThreeLinks();
        // Initially, given 2 tick interval, nothing should be in the lists
        discoveryPlan = dm.makeDiscoveryPlan();
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
    public void handleIslDiscoOtherEnd() {
        // This is to test that we correctly handle the scenario where one side of an ISL comes up,
        // and we want to make sure the other side comes up correctly.
        //
        // This models the behavior from OFELinkBolt.
        // TODO: change this test when the behavior moves from OFELinkBolt to DiscoveryManager.


        // What to test?  Assume we have 2 nodes, and we just received Discovered on one.
        // Ensure that the other one starts discovery as well.
        // We can use forlorn to create the "stop discovery" scenario, then see if it changes.
        setupThreeLinks();

        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // push one of them to be forlorned..
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // 1 & 2 after
        dm.makeDiscoveryPlan();                                      // 2 & 3 after

        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // 1 & 4 after
        dm.makeDiscoveryPlan();                                      // n3 @5 attempts, 3 cons.fails

        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        dm.handleDiscovered(srcNode2.getSwitchDpId(), srcNode2.getPortId(),
                dstNode2.getSwitchDpId(), dstNode2.getPortId()); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                      // link3 forlorned..
        assertEquals(2, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Now link3 is forlorned .. let's get it back into the discovery;
        // Let's act like link1 is connected to link3, and link1 was just discovered..
        dm.handleDiscovered(srcNode1.getSwitchDpId(), srcNode1.getPortId(),
                dstNode1.getSwitchDpId(), dstNode1.getPortId()); // clear attempts
        // This part mirrors what happens in OFELinkBolt *if* there was a state change on src.
        if (!dm.checkForIsl(srcNode3.getSwitchDpId(), srcNode3.getPortId())) {
            // Only call PortUp if we aren't checking for ISL. Otherwise, we could end up in an
            // infinite cycle of always sending a Port UP when one side is discovered.
            dm.handlePortUp(srcNode3.getSwitchDpId(), srcNode3.getPortId());
        }
        discoveryPlan = dm.makeDiscoveryPlan();                      // link3 not forlorned
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

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