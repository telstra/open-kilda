package org.bitbucket.openkilda.pce.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.PathComputerMock;
import org.bitbucket.openkilda.pce.StateStorageMock;
import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Function;

public class FlowManagerTest {
    private final StateStorageMock storage = new StateStorageMock();
    private final PathComputer computer = new PathComputerMock();
    private final NetworkManager networkManager = new NetworkManager(storage);
    private final FlowManager flowManager = new FlowManager(storage, networkManager, computer);

    private final Switch sw1 = new Switch("sw1", "", "", "", SwitchState.ACTIVATED, "localhost");
    private final Switch sw2 = new Switch("sw2", "", "", "", SwitchState.ACTIVATED, "localhost");
    private final Switch sw3 = new Switch("sw3", "", "", "", SwitchState.ADDED, "remote");
    private final Switch sw4 = new Switch("sw4", "", "", "", SwitchState.ADDED, "remote");
    private final Switch sw5 = new Switch("sw5", "", "", "", SwitchState.REMOVED, "remote");

    private final Isl isl12 = new Isl("sw1", "sw2", 1, 2, 1L, 0L, 10);
    private final Isl isl21 = new Isl("sw2", "sw1", 2, 1, 1L, 0L, 10);

    private final Isl isl14 = new Isl("sw1", "sw4", 2, 1, 1L, 0L, 10);
    private final Isl isl41 = new Isl("sw4", "sw1", 1, 2, 1L, 0L, 10);
    private final Isl isl24 = new Isl("sw2", "sw4", 3, 2, 1L, 0L, 10);
    private final Isl isl42 = new Isl("sw4", "sw2", 2, 3, 1L, 0L, 10);

    private final Isl isl54 = new Isl("sw5", "sw4", 1, 3, 1L, 0L, 10);
    private final Isl isl45 = new Isl("sw4", "sw5", 3, 1, 1L, 0L, 10);
    private final Isl isl52 = new Isl("sw5", "sw2", 2, 4, 1L, 0L, 10);
    private final Isl isl25 = new Isl("sw2", "sw5", 4, 2, 1L, 0L, 10);
    private final Isl isl53 = new Isl("sw5", "sw3", 3, 1, 1L, 0L, 10);
    private final Isl isl35 = new Isl("sw3", "sw5", 1, 3, 1L, 0L, 10);

    private final Flow firstFlow = new Flow("first-flow", 0, "first-flow", "sw1", "sw3", 11, 11, 100, 200);
    private final Flow secondFlow = new Flow("second-flow", 0, "second-flow", "sw5", "sw3", 12, 12, 100, 200);
    private final Flow thirdFlow = new Flow("third-flow", 0, "third-flow", "sw3", "sw3", 21, 22, 100, 200);
    private final Flow forwardCreatedFlow = new Flow("created-flow", 0, 10L, "description",
            "timestamp", "sw3", "sw3", 21, 22, 100, 200, 4, 4, new LinkedList<>());
    private final Flow reverseCreatedFlow = new Flow("created-flow", 0, 10L, "description",
            "timestamp", "sw3", "sw3", 22, 21, 200, 100, 5, 5, new LinkedList<>());

    @Before
    public void setUp() {
        buildNetworkTopology(networkManager);
    }

    @After
    public void tearDown() {
        networkManager.clear();
        flowManager.clear();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getNotExistentFlow() throws Exception {
        flowManager.getFlow(firstFlow.getFlowId());
    }

    @Test
    public void getFlow() throws Exception {
        ImmutablePair<Flow, Flow> newFlow = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> storedFlow = flowManager.getFlow(firstFlow.getFlowId());
        assertEquals(newFlow, storedFlow);
    }

    @Test
    public void createFlow() throws Exception {
        ImmutablePair<Flow, Flow> newFlow = flowManager.createFlow(firstFlow);

        Flow forward = newFlow.left;
        ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> path = flowManager.getPath(
                firstFlow.getSourceSwitch(), firstFlow.getDestinationSwitch(), firstFlow.getBandwidth());

        assertEquals(1 | FlowManager.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(2, forward.getTransitVlan());
        assertEquals(1, forward.getMeterId());
        assertEquals(path.getLeft(), forward.getFlowPath());

        Flow reverse = newFlow.right;
        assertEquals(1 | FlowManager.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(3, reverse.getTransitVlan());
        assertEquals(1, reverse.getMeterId());
        assertEquals(path.getRight(), reverse.getFlowPath());

        assertEquals(1, storage.getFlowsCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createAlreadyExistentFlow() throws Exception {
        flowManager.createFlow(firstFlow);
        flowManager.createFlow(firstFlow);
    }

    @Test
    public void deleteFlow() throws Exception {
        ImmutablePair<Flow, Flow> newFlow = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> oldFlow = flowManager.deleteFlow(firstFlow.getFlowId());
        assertEquals(newFlow, oldFlow);
        assertEquals(0, storage.getFlowsCount());
    }

    @Test
    public void updateFlow() throws Exception {
        ImmutablePair<Flow, Flow> oldFlow = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> newFlow = flowManager.updateFlow(firstFlow.getFlowId(), firstFlow);
        assertNotEquals(newFlow, oldFlow);
        assertEquals(1, storage.getFlowsCount());

        newFlow.left.setLastUpdated("");
        newFlow.right.setLastUpdated("");
        oldFlow.left.setLastUpdated("");
        oldFlow.right.setLastUpdated("");
        assertEquals(oldFlow, newFlow);
    }

    @Test
    public void dumpFlows() throws Exception {
        ImmutablePair<Flow, Flow> first = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> second = flowManager.createFlow(secondFlow);
        ImmutablePair<Flow, Flow> third = flowManager.createFlow(thirdFlow);
        assertEquals(new HashSet<>(Arrays.asList(first, second, third)), flowManager.dumpFlows());
    }

    @Test
    public void getFlowPath() throws Exception {
        flowManager.createFlow(firstFlow);
        ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> path = flowManager.getPath(
                firstFlow.getSourceSwitch(), firstFlow.getDestinationSwitch(), firstFlow.getBandwidth());
        assertEquals(path, flowManager.getFlowPath(firstFlow.getFlowId()));
    }

    @Test
    public void getAffectedBySwitchFlows() throws Exception {
        Set<ImmutablePair<Flow, Flow>> affected;
        ImmutablePair<Flow, Flow> first = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> second = flowManager.createFlow(secondFlow);
        ImmutablePair<Flow, Flow> third = flowManager.createFlow(thirdFlow);

        affected = flowManager.getAffectedBySwitchFlows(sw5.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);

        affected = flowManager.getAffectedBySwitchFlows(sw3.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(first, second, third)), affected);

        affected = flowManager.getAffectedBySwitchFlows(sw1.getSwitchId());
        assertEquals(Collections.singleton(first), affected);
    }

    @Test
    public void getAffectedByIslFlows() throws Exception {
        Set<ImmutablePair<Flow, Flow>> affected;
        ImmutablePair<Flow, Flow> first = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> second = flowManager.createFlow(secondFlow);
        flowManager.createFlow(thirdFlow);

        affected = flowManager.getAffectedByIslFlows(isl12.getId());
        assertEquals(Collections.singleton(first), affected);

        affected = flowManager.getAffectedByIslFlows(isl21.getId());
        assertEquals(Collections.singleton(first), affected);

        affected = flowManager.getAffectedByIslFlows(isl53.getId());
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);

        affected = flowManager.getAffectedByIslFlows(isl35.getId());
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);
    }

    @Test
    public void getPath() throws Exception {
        ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> path = flowManager.getPath(sw1, sw3, 0);
        assertEquals(new LinkedList<>(Arrays.asList(isl12, isl25, isl53)), path.left);
        assertEquals(new LinkedList<>(Arrays.asList(isl35, isl52, isl21)), path.right);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getPathIntersection() throws Exception {
        networkManager.createOrUpdateIsl(isl14);
        networkManager.createOrUpdateIsl(isl41);

        ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> path1 = flowManager.getPath(sw4, sw3, 5);
        assertEquals(new LinkedList<>(Arrays.asList(isl45, isl53)), path1.left);
        assertEquals(new LinkedList<>(Arrays.asList(isl35, isl54)), path1.right);

        ImmutablePair<LinkedList<Isl>, LinkedList<Isl>> path2 = flowManager.getPath(sw2, sw3, 5);
        assertEquals(new LinkedList<>(Arrays.asList(isl25, isl53)), path2.left);
        assertEquals(new LinkedList<>(Arrays.asList(isl35, isl52)), path2.right);

        ImmutablePair<Set<Isl>, Set<Isl>> expected = new ImmutablePair<>(
                new HashSet<>(Arrays.asList(isl53)), new HashSet<>(Arrays.asList(isl35)));
        assertEquals(expected, flowManager.getPathIntersection(path1, path2));
        assertEquals(expected, flowManager.getPathIntersection(path2, path1));

        flowManager.getPath(sw5, sw3, 1);
    }

    @Test
    public void allocateFlow() throws Exception {
        flowManager.allocateFlow(new ImmutablePair<>(forwardCreatedFlow, reverseCreatedFlow));
        flowManager.allocateFlow(new ImmutablePair<>(forwardCreatedFlow, reverseCreatedFlow));

        Set<Integer> allocatedCookies = flowManager.resourceManager.getAllCookies();
        Set<Integer> allocatedVlanIds = flowManager.resourceManager.getAllVlanIds();
        Set<Integer> allocatedMeterIds = flowManager.resourceManager.getAllMeterIds(sw3.getSwitchId());

        Set<Integer> expectedCookies = new HashSet<>(Arrays.asList(
                (int) forwardCreatedFlow.getCookie(),
                (int) reverseCreatedFlow.getCookie()));

        Set<Integer> expectedVlanIds = new HashSet<>(Arrays.asList(
                forwardCreatedFlow.getTransitVlan(),
                reverseCreatedFlow.getTransitVlan()));

        Set<Integer> expectedMeterIds = new HashSet<>(Arrays.asList(
                forwardCreatedFlow.getMeterId(),
                reverseCreatedFlow.getMeterId()));

        assertEquals(expectedCookies, allocatedCookies);
        assertEquals(expectedVlanIds, allocatedVlanIds);
        assertEquals(expectedMeterIds, allocatedMeterIds);
    }

    @Test
    public void deallocateFlow() throws Exception {
        allocateFlow();
        flowManager.deallocateFlow(new ImmutablePair<>(forwardCreatedFlow, reverseCreatedFlow));
        flowManager.deallocateFlow(new ImmutablePair<>(forwardCreatedFlow, reverseCreatedFlow));

        Set<Integer> allocatedCookies = flowManager.resourceManager.getAllCookies();
        Set<Integer> allocatedVlanIds = flowManager.resourceManager.getAllVlanIds();
        Set<Integer> allocatedMeterIds = flowManager.resourceManager.getAllMeterIds(sw3.getSwitchId());

        assertEquals(Collections.emptySet(), allocatedCookies);
        assertEquals(Collections.emptySet(), allocatedVlanIds);
        assertEquals(Collections.emptySet(), allocatedMeterIds);
    }

    @Test
    public void eventTest() {
        FlowManager otherFlowManager = new FlowManager(storage, networkManager, computer);
        Function<FlowManager.FlowChangeEvent, Void> switchChangeCallback =
                new Function<FlowManager.FlowChangeEvent, Void>() {
                    @Override
                    public Void apply(FlowManager.FlowChangeEvent flowChangeEvent) {
                        System.out.println(flowChangeEvent);
                        otherFlowManager.handleFlowChange(flowChangeEvent);
                        return null;
                    }
                };

        flowManager.withFlowChange(switchChangeCallback);

        flowManager.createFlow(firstFlow);
        flowManager.createFlow(secondFlow);
        flowManager.createFlow(thirdFlow);
        flowManager.updateFlow(firstFlow.getFlowId(), firstFlow);
        flowManager.deleteFlow(secondFlow.getFlowId());
        flowManager.updateFlow(thirdFlow.getFlowId(), thirdFlow);

        assertEquals(flowManager.dumpFlows(), otherFlowManager.dumpFlows());
    }

    private void buildNetworkTopology(NetworkManager networkManager) {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createSwitch(sw3);
        networkManager.createSwitch(sw4);
        networkManager.createSwitch(sw5);
        networkManager.createOrUpdateIsl(isl12);
        networkManager.createOrUpdateIsl(isl21);
        networkManager.createOrUpdateIsl(isl24);
        networkManager.createOrUpdateIsl(isl42);
        networkManager.createOrUpdateIsl(isl52);
        networkManager.createOrUpdateIsl(isl25);
        networkManager.createOrUpdateIsl(isl53);
        networkManager.createOrUpdateIsl(isl35);
        networkManager.createOrUpdateIsl(isl54);
        networkManager.createOrUpdateIsl(isl45);
    }
}
