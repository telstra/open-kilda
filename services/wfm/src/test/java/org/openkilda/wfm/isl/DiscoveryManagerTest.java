package org.openkilda.wfm.isl;

import org.junit.Before;
import org.junit.Test;
import org.openkilda.messaging.model.DiscoveryLink;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The DiscoveryManager is the main class that governs ISL discovery. It develops a list of
 * switch/ports to send discovery/health checks on, and a list of failures to notify others about.
 *
 * OFELinkBolt is the primary user of this class, leveraging DiscoveryManager.Plan
 *
 * The primary test scenarios that are of interest..
 *
 *
 */
public class DiscoveryManagerTest {

    DiscoveryManager dm;
    DiscoveryLink link1;
    DiscoveryLink link2;
    DiscoveryLink link3;
    int islHealthCheckInterval;
    int islHealthFailureLimit;
    int forlornLimit;

    @Before
    public void setUp() throws Exception {
        islHealthCheckInterval = 0; // means check ever tick
        islHealthFailureLimit  = 1; // for testing, failure after 1 tick;
        forlornLimit = 2;

        dm = new DiscoveryManager(
                new DummyIIslFilter(), new LinkedList<>(), islHealthCheckInterval,
                islHealthFailureLimit, forlornLimit
        );
    }

    /** several tests start with creating/adding 3 ports */
    public void setupThreeLinks(){
        link1 = new DiscoveryLink("sw1", 1, "sw3", 1, islHealthCheckInterval, forlornLimit);
        link2 = new DiscoveryLink("sw1", 2, "sw3", 2, islHealthCheckInterval, forlornLimit);
        link3 = new DiscoveryLink("sw2", 1, "sw3", 3, islHealthCheckInterval, forlornLimit);
        dm.handlePortUp(link1.getSrcSwitch(), link1.getSrcPort());
        dm.handlePortUp(link2.getSrcSwitch(), link2.getSrcPort());
        dm.handlePortUp(link3.getSrcSwitch(), link3.getSrcPort());
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
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort()); // clear attempts
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), link2.getDstSwitch(), link2.getDstPort()); // clear attempts
        dm.handleDiscovered(link3.getSrcSwitch(), link3.getSrcPort(), link3.getDstSwitch(), link3.getDstPort()); // clear attempts
        dm.makeDiscoveryPlan();                                      // increase attempts of all to 1
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort()); // clear attempts
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), link2.getDstSwitch(), link2.getDstPort()); // clear attempts
        dm.makeDiscoveryPlan();                                      // attempts: link1&2 @ 1, link3 @ 2
        discoveryPlan = dm.makeDiscoveryPlan();                      // the attempts test is based on the 1 & 2 count
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(1, discoveryPlan.discoveryFailure.size());
        assertEquals(link3.getSrcSwitch(),discoveryPlan.discoveryFailure.get(0).switchId);
        assertEquals(link3.getSrcPort(),discoveryPlan.discoveryFailure.get(0).portId);

        // Now verify it doesn't re-send the failure
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), null, 0); // clear attempts
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), null, 0); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                      // the attempts test is based on the 0 & 3 count
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Now test forlorn
        dm.makeDiscoveryPlan();                                       // the attempts test is based on the 1 & 4 count
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), null, 0); // clear attempts
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), null, 0); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                       // now consecutive failure at 3 for link3
        assertEquals(2, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());


        // verify Health Check Interval is working properly.
        islHealthCheckInterval = 2;
        islHealthFailureLimit  = 4; // for testing, failure after 1 tick;
        forlornLimit = 8;

        dm = new DiscoveryManager(
                new DummyIIslFilter(), new LinkedList<>(), islHealthCheckInterval,
                islHealthFailureLimit, forlornLimit
        );
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

        // An initial success is state change
        assertEquals(true, dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(),
                link2.getDstSwitch(), link2.getDstPort()));
        // A repeated success is not
        assertEquals(false, dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(),
                link2.getDstSwitch(), link2.getDstPort()));

        // Let it fail, then succeed .. both are state changes
        assertEquals(true, dm.handleFailed(link2.getSrcSwitch(), link2.getSrcPort()));
        assertEquals(true, dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(),
                link2.getDstSwitch(), link2.getDstPort()));
        assertEquals(false, dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(),
                link2.getDstSwitch(), link2.getDstPort()));
    }

    @Test
    public void handleFailed() {
        // Test whether it handle the state change properly .. ie is this a new failure or not.
        // NB: handleFailed is called when an ISL failure is received from FL; the other kind of
        //      failure is if there is no response, and that is handled by makeDiscoveryPlan().
        List<DiscoveryLink> nodes;
        setupThreeLinks();

        // After a PortUP, a failure isn't a state change, the default is to assume it isn't an ISL
        assertEquals(false, dm.handleFailed(link2.getSrcSwitch(), link2.getSrcPort()));
        // A success after a failure is state change
        assertEquals(true, dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(),
                link2.getDstSwitch(), link2.getDstPort()));
        // A failure after a success is state change
        assertEquals(true, dm.handleFailed(link2.getSrcSwitch(), link2.getSrcPort()));
        // Repeated failures isn't a state change.
        assertEquals(false, dm.handleFailed(link2.getSrcSwitch(), link2.getSrcPort()));
    }

    @Test
    public void handleSwitchUp() {
        // Verify that all switch/ports on this switch of the ISL flag cleared

        // Generally speaking, nothing interesting happens on a SwitchUp, since all of the action is
        // in PortUp.  However, this one area, when pre-existing ISLs are already in the DM, should
        // be tested. We need to create some ISLs, then send the SwitchUp, and confirm isFoundIsl
        // is cleared.
        List<DiscoveryLink> nodes;
        setupThreeLinks();

        // discover them and confirm all discovered
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort());
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), link2.getDstSwitch(), link2.getDstPort());
        dm.handleDiscovered(link3.getSrcSwitch(), link3.getSrcPort(), link3.getDstSwitch(), link3.getDstPort());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(link1.getSrcSwitch(), 0));
        assertEquals(true, nodes.get(0).isDiscovered());
        assertEquals(true, nodes.get(1).isDiscovered());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(link3.getSrcSwitch(), 0));
        assertEquals(true, nodes.get(0).isDiscovered());

        // now send SwitchUp and confirm sw1 all go back to not found, sw2 unchanged
        dm.handleSwitchUp(link1.getSrcSwitch());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(link1.getSrcSwitch(), 0));
        assertEquals(false, nodes.get(0).isDiscovered());
        assertEquals(false , nodes.get(1).isDiscovered());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(link3.getSrcSwitch(), 0));
        assertEquals(true, nodes.get(0).isDiscovered());

        // now confirm they go back to found upon next Discovery.
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort());
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), link2.getDstSwitch(), link2.getDstPort());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(link1.getSrcSwitch(), 0));
        assertEquals(true, nodes.get(0).isDiscovered());
        assertEquals(true, nodes.get(1).isDiscovered());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(link3.getSrcSwitch(), 0));
        assertEquals(true, nodes.get(0).isDiscovered());
    }

    @Test
    public void handleSwitchDown() {
        // verify all the ISL switch/ports are deleted
        List<DiscoveryLink> nodes;
        setupThreeLinks();

        // 3 nodes - 2 in sw1, one in sw2; verify dropping sw1 drops 2 nodes (1 remaining)
        nodes = dm.findBySwitch(link1.getSrcSwitch());
        assertEquals(2, nodes.size());
        nodes = dm.findBySwitch(link3.getSrcSwitch());
        assertEquals(1, nodes.size());

        // Drop the switch, and then the same 4 lines of code, except 0 size for sw1 nodes.
        dm.handleSwitchDown(link1.getSrcSwitch());
        nodes = dm.findBySwitch(link1.getSrcSwitch());
        assertEquals(0, nodes.size());
        nodes = dm.findBySwitch((link3.getSrcSwitch()));
        assertEquals(1, nodes.size());
    }

    @Test
    public void handlePortUp() {
        // verify the switch/port is added
        // verify that adding an existing one doesn't crash it.
        List<DiscoveryLink> nodes;

        // Put in 1 node and verify it is there.
        DiscoveryLink node = new DiscoveryLink("sw1", 1, islHealthCheckInterval, islHealthFailureLimit);
        dm.handlePortUp(node.getSrcSwitch(), node.getSrcPort());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(node.getSrcSwitch(), node.getSrcPort()));
        assertEquals(1, nodes.size());
        assertEquals(node, nodes.get(0));

        // try to add it back in .. should still only be 1
        dm.handlePortUp(node.getSrcSwitch(), node.getSrcPort());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(node.getSrcSwitch(), node.getSrcPort()));
        assertEquals(1, nodes.size());
        assertEquals(node,nodes.get(0));
    }

    @Test
    public void handlePortDown() {
        // verify the switch/port is deleted.
        // verify remove one that doesn't exist doesn't crash it
        List<DiscoveryLink> nodes;

        // Put in 1 node and then remove it. The handlePortUp test ensures the Port Up works.
        DiscoveryLink node = new DiscoveryLink("sw1", 1, islHealthCheckInterval, islHealthFailureLimit);
        dm.handlePortUp(node.getSrcSwitch(), node.getSrcPort());
        dm.handlePortDown(node.getSrcSwitch(), node.getSrcPort());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(node.getSrcSwitch(), node.getSrcPort()));
        assertEquals(0, nodes.size());

        // call PortDown again .. verify nothing bad happens.
        dm.handlePortDown(node.getSrcSwitch(), node.getSrcPort());
        nodes = dm.findBySwitch(new DiscoveryManager.Node(node.getSrcSwitch(), node.getSrcPort()));
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
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort()); // clear attempts
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), link2.getDstSwitch(), link2.getDstPort()); // clear attempts
        dm.makeDiscoveryPlan();                                      // 1 & 2 after
        dm.makeDiscoveryPlan();                                      // 2 & 3 after
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort()); // clear attempts
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), link2.getDstSwitch(), link2.getDstPort()); // clear attempts
        dm.makeDiscoveryPlan();                                      // 1 & 4 after
        dm.makeDiscoveryPlan();                                      // n3 @5 attempts, 3 cons.fails
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort()); // clear attempts
        dm.handleDiscovered(link2.getSrcSwitch(), link2.getSrcPort(), link2.getDstSwitch(), link2.getDstPort()); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                      // link3 forlorned..
        assertEquals(2, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Now link3 is forlorned .. let's get it back into the discovery;
        // Let's act like link1 is connected to link3, and link1 was just discovered..
        dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(), link1.getDstSwitch(), link1.getDstPort()); // clear attempts
        // This part mirrors what happens in OFELinkBolt *if* there was a state change on src.
        if (!dm.checkForIsl(link3.getSrcSwitch(), link3.getSrcPort())){
            // Only call PortUp if we aren't checking for ISL. Otherwise, we could end up in an
            // infinite cycle of always sending a Port UP when one side is discovered.
            dm.handlePortUp(link3.getSrcSwitch(), link3.getSrcPort());
        }
        discoveryPlan = dm.makeDiscoveryPlan();                      // link3 not forlorned
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

    }

    @Test
    public void shouldWorksCorrectlyWhenIslUpdating() {
        setupThreeLinks();

        boolean stateChanged = dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(),
                link1.getDstSwitch(), link1.getDstPort());
        assertTrue(stateChanged);

        stateChanged = dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(),
                link1.getDstSwitch(), link1.getDstPort());
        assertFalse(stateChanged);

        stateChanged = dm.handleDiscovered(link1.getSrcSwitch(), link1.getSrcPort(),
                link2.getDstSwitch(), link2.getDstPort());
        assertTrue(stateChanged);
    }

}