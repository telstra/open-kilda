package org.openkilda.wfm.isl;

import org.junit.Before;
import org.junit.Test;
import org.openkilda.messaging.model.DiscoveryNode;

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
    DiscoveryNode node1;
    DiscoveryNode node2;
    DiscoveryNode node3;
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
    public void setupThreeNodes(){
        node1 = new DiscoveryNode("sw1", "pt1", islHealthCheckInterval, forlornLimit);
        node2 = new DiscoveryNode("sw1", "pt2", islHealthCheckInterval, forlornLimit);
        node3 = new DiscoveryNode("sw2", "pt1", islHealthCheckInterval, forlornLimit);
        dm.handlePortUp(node1.getSwitchId(), node1.getPortId());
        dm.handlePortUp(node2.getSwitchId(), node2.getPortId());
        dm.handlePortUp(node3.getSwitchId(), node3.getPortId());
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
        List<DiscoveryNode> nodes;
        setupThreeNodes();

        // Initially, given 0 tick interval, everything should be in discoveryPlan and no failures
        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // In order for one of them to be in failure plan, it has to have been a success beforehand
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()); // clear attempts
        dm.handleDiscovered(node3.getSwitchId(), node3.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // increase attempts of all to 1
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // attempts: node1&2 @ 1, node3 @ 2
        discoveryPlan = dm.makeDiscoveryPlan();                      // the attempts test is based on the 1 & 2 count
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(1, discoveryPlan.discoveryFailure.size());
        assertEquals(node3.getSwitchId(),discoveryPlan.discoveryFailure.get(0).switchId);
        assertEquals(node3.getPortId(),discoveryPlan.discoveryFailure.get(0).portId);

        // Now verify it doesn't re-send the failure
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                      // the attempts test is based on the 0 & 3 count
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Now test forlorn
        dm.makeDiscoveryPlan();                                       // the attempts test is based on the 1 & 4 count
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                       // now consecutive failure at 3 for node3
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
        setupThreeNodes();
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

        setupThreeNodes();

        // An initial success is state change
        assertEquals(true, dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()));
        // A repeated success is not
        assertEquals(false, dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()));

        // Let it fail, then succeed .. both are state changes
        assertEquals(true, dm.handleFailed(node2.getSwitchId(), node2.getPortId()));
        assertEquals(true, dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()));
        assertEquals(false, dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()));
    }

    @Test
    public void handleFailed() {
        // Test whether it handle the state change properly .. ie is this a new failure or not.
        // NB: handleFailed is called when an ISL failure is received from FL; the other kind of
        //      failure is if there is no response, and that is handled by makeDiscoveryPlan().
        List<DiscoveryNode> nodes;
        setupThreeNodes();

        // After a PortUP, a failure isn't a state change, the default is to assume it isn't an ISL
        assertEquals(false, dm.handleFailed(node2.getSwitchId(), node2.getPortId()));
        // A success after a failure is state change
        assertEquals(true, dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()));
        // A failure after a success is state change
        assertEquals(true, dm.handleFailed(node2.getSwitchId(), node2.getPortId()));
        // Repeated failures isn't a state change.
        assertEquals(false, dm.handleFailed(node2.getSwitchId(), node2.getPortId()));
    }

    @Test
    public void handleSwitchUp() {
        // Verify that all switch/ports on this switch of the ISL flag cleared

        // Generally speaking, nothing interesting happens on a SwitchUp, since all of the action is
        // in PortUp.  However, this one area, when pre-existing ISLs are already in the DM, should
        // be tested. We need to create some ISLs, then send the SwitchUp, and confirm isFoundIsl
        // is cleared.
        List<DiscoveryNode> nodes;
        setupThreeNodes();

        // discover them and confirm all discovered
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId());
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId());
        dm.handleDiscovered(node3.getSwitchId(), node3.getPortId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node1.getSwitchId(), null));
        assertEquals(true, nodes.get(0).isFoundIsl());
        assertEquals(true, nodes.get(1).isFoundIsl());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node3.getSwitchId(), null));
        assertEquals(true, nodes.get(0).isFoundIsl());

        // now send SwitchUp and confirm sw1 all go back to not found, sw2 unchanged
        dm.handleSwitchUp(node1.getSwitchId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node1.getSwitchId(), null));
        assertEquals(false, nodes.get(0).isFoundIsl());
        assertEquals(false , nodes.get(1).isFoundIsl());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node3.getSwitchId(), null));
        assertEquals(true, nodes.get(0).isFoundIsl());

        // now confirm they go back to found upon next Discovery.
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId());
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node1.getSwitchId(), null));
        assertEquals(true, nodes.get(0).isFoundIsl());
        assertEquals(true, nodes.get(1).isFoundIsl());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node3.getSwitchId(), null));
        assertEquals(true, nodes.get(0).isFoundIsl());
    }

    @Test
    public void handleSwitchDown() {
        // verify all the ISL switch/ports are deleted
        List<DiscoveryNode> nodes;
        setupThreeNodes();

        // 3 nodes - 2 in sw1, one in sw2; verify dropping sw1 drops 2 nodes (1 remaining)
        nodes = dm.filterQueue(new DiscoveryManager.Node(node1.getSwitchId(), null));
        assertEquals(2, nodes.size());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node3.getSwitchId(), null));
        assertEquals(1, nodes.size());

        // Drop the switch, and then the same 4 lines of code, except 0 size for sw1 nodes.
        dm.handleSwitchDown(node1.getSwitchId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node1.getSwitchId(), null));
        assertEquals(0, nodes.size());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node3.getSwitchId(), null));
        assertEquals(1, nodes.size());
    }

    @Test
    public void handlePortUp() {
        // verify the switch/port is added
        // verify that adding an existing one doesn't crash it.
        List<DiscoveryNode> nodes;

        // Put in 1 node and verify it is there.
        DiscoveryNode node = new DiscoveryNode("sw1", "pt1", islHealthCheckInterval, islHealthFailureLimit);
        dm.handlePortUp(node.getSwitchId(), node.getPortId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node.getSwitchId(), node.getPortId()));
        assertEquals(1, nodes.size());
        assertEquals(node,nodes.get(0));

        // try to add it back in .. should still only be 1
        dm.handlePortUp(node.getSwitchId(), node.getPortId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node.getSwitchId(), node.getPortId()));
        assertEquals(1, nodes.size());
        assertEquals(node,nodes.get(0));
    }

    @Test
    public void handlePortDown() {
        // verify the switch/port is deleted.
        // verify remove one that doesn't exist doesn't crash it
        List<DiscoveryNode> nodes;

        // Put in 1 node and then remove it. The handlePortUp test ensures the Port Up works.
        DiscoveryNode node = new DiscoveryNode("sw1", "pt1", islHealthCheckInterval, islHealthFailureLimit);
        dm.handlePortUp(node.getSwitchId(), node.getPortId());
        dm.handlePortDown(node.getSwitchId(), node.getPortId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node.getSwitchId(), node.getPortId()));
        assertEquals(0, nodes.size());

        // call PortDown again .. verify nothing bad happens.
        dm.handlePortDown(node.getSwitchId(), node.getPortId());
        nodes = dm.filterQueue(new DiscoveryManager.Node(node.getSwitchId(), node.getPortId()));
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
        setupThreeNodes();

        DiscoveryManager.Plan discoveryPlan = dm.makeDiscoveryPlan();
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // push one of them to be forlorned..
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // 1 & 2 after
        dm.makeDiscoveryPlan();                                      // 2 & 3 after
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()); // clear attempts
        dm.makeDiscoveryPlan();                                      // 1 & 4 after
        dm.makeDiscoveryPlan();                                      // n3 @5 attempts, 3 cons.fails
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        dm.handleDiscovered(node2.getSwitchId(), node2.getPortId()); // clear attempts
        discoveryPlan = dm.makeDiscoveryPlan();                      // node3 forlorned..
        assertEquals(2, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

        // Now node3 is forlorned .. let's get it back into the discovery;
        // Let's act like node1 is connected to node3, and node1 was just discovered..
        dm.handleDiscovered(node1.getSwitchId(), node1.getPortId()); // clear attempts
        // This part mirrors what happens in OFELinkBolt *if* there was a state change on src.
        if (!dm.checkForIsl(node3.getSwitchId(),node3.getPortId())){
            // Only call PortUp if we aren't checking for ISL. Otherwise, we could end up in an
            // infinite cycle of always sending a Port UP when one side is discovered.
            dm.handlePortUp(node3.getSwitchId(),node3.getPortId());
        }
        discoveryPlan = dm.makeDiscoveryPlan();                      // node3 not forlorned
        assertEquals(3, discoveryPlan.needDiscovery.size());
        assertEquals(0, discoveryPlan.discoveryFailure.size());

    }
}