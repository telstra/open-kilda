package org.bitbucket.openkilda.pce.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.bitbucket.openkilda.messaging.info.event.IslChangeType;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;
import org.bitbucket.openkilda.pce.NetworkTopologyConstants;
import org.bitbucket.openkilda.pce.PathComputerMock;
import org.bitbucket.openkilda.pce.StateStorageMock;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class FlowManagerTest {
    private final StateStorageMock storage = new StateStorageMock();
    private final PathComputer computer = new PathComputerMock();
    private final NetworkManager networkManager = new NetworkManager(storage, computer);
    private final FlowManager flowManager = new FlowManager(storage, networkManager);

    private final Flow firstFlow = new Flow("first-flow", 0, "first-flow", "sw1", 11, 100, "sw3", 11, 200);
    private final Flow secondFlow = new Flow("second-flow", 0, "second-flow", "sw5", 12, 100, "sw3", 12, 200);
    private final Flow thirdFlow = new Flow("third-flow", 0, "third-flow", "sw3", 21, 100, "sw3", 22, 200);
    private final Flow forwardCreatedFlow = new Flow("created-flow", 0, 10L, "description",
            "timestamp", "sw3", "sw3", 21, 22, 100, 200, 4, 4, new PathInfoData(), FlowState.ALLOCATED);
    private final Flow reverseCreatedFlow = new Flow("created-flow", 0, 10L, "description",
            "timestamp", "sw3", "sw3", 22, 21, 200, 100, 5, 5, new PathInfoData(), FlowState.ALLOCATED);

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
        ImmutablePair<PathInfoData, PathInfoData> path = flowManager.getPath(
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
        assertEquals(1, flowManager.resourceCache.getAllMeterIds("sw1").size());
        assertEquals(0, flowManager.resourceCache.getAllMeterIds("sw2").size());
        assertEquals(1, flowManager.resourceCache.getAllMeterIds("sw3").size());
        assertEquals(0, flowManager.resourceCache.getAllMeterIds("sw4").size());
        assertEquals(0, flowManager.resourceCache.getAllMeterIds("sw5").size());
        assertEquals(2, flowManager.resourceCache.getAllVlanIds().size());
        assertEquals(1, flowManager.resourceCache.getAllCookies().size());

        ImmutablePair<Flow, Flow> newFlow = flowManager.updateFlow(firstFlow.getFlowId(), firstFlow);
        assertEquals(1, flowManager.resourceCache.getAllMeterIds("sw1").size());
        assertEquals(0, flowManager.resourceCache.getAllMeterIds("sw2").size());
        assertEquals(1, flowManager.resourceCache.getAllMeterIds("sw3").size());
        assertEquals(0, flowManager.resourceCache.getAllMeterIds("sw4").size());
        assertEquals(0, flowManager.resourceCache.getAllMeterIds("sw5").size());
        assertEquals(2, flowManager.resourceCache.getAllVlanIds().size());
        assertEquals(1, flowManager.resourceCache.getAllCookies().size());

        assertEquals(1, storage.getFlowsCount());

        assertNotEquals(newFlow.hashCode(), oldFlow.hashCode());
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
        ImmutablePair<PathInfoData, PathInfoData> path = flowManager.getPath(
                firstFlow.getSourceSwitch(), firstFlow.getDestinationSwitch(), firstFlow.getBandwidth());
        assertEquals(path, flowManager.getFlowPath(firstFlow.getFlowId()));
    }

    @Test
    public void getAffectedBySwitchFlows() throws Exception {
        Set<ImmutablePair<Flow, Flow>> affected;
        ImmutablePair<Flow, Flow> first = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> second = flowManager.createFlow(secondFlow);
        ImmutablePair<Flow, Flow> third = flowManager.createFlow(thirdFlow);

        affected = flowManager.getAffectedBySwitchFlows(NetworkTopologyConstants.sw5.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);

        affected = flowManager.getAffectedBySwitchFlows(NetworkTopologyConstants.sw3.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(first, second, third)), affected);

        affected = flowManager.getAffectedBySwitchFlows(NetworkTopologyConstants.sw1.getSwitchId());
        assertEquals(Collections.singleton(first), affected);
    }

    @Test
    public void getAffectedByIslFlows() throws Exception {
        Set<ImmutablePair<Flow, Flow>> affected;
        ImmutablePair<Flow, Flow> first = flowManager.createFlow(firstFlow);
        ImmutablePair<Flow, Flow> second = flowManager.createFlow(secondFlow);
        flowManager.createFlow(thirdFlow);

        affected = flowManager.getAffectedByIslFlows(NetworkTopologyConstants.isl12);
        assertEquals(Collections.singleton(first), affected);

        affected = flowManager.getAffectedByIslFlows(NetworkTopologyConstants.isl21);
        assertEquals(Collections.singleton(first), affected);

        affected = flowManager.getAffectedByIslFlows(NetworkTopologyConstants.isl53);
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);

        affected = flowManager.getAffectedByIslFlows(NetworkTopologyConstants.isl35);
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);
    }

    @Test
    public void getPath() throws Exception {
        ImmutablePair<PathInfoData, PathInfoData> path = flowManager.getPath(
                NetworkTopologyConstants.sw1, NetworkTopologyConstants.sw3, 0);

        List<PathNode> direct = new ArrayList<>();
        direct.addAll(NetworkTopologyConstants.isl12.getPath());
        direct.addAll(NetworkTopologyConstants.isl25.getPath());
        direct.addAll(NetworkTopologyConstants.isl53.getPath());
        PathInfoData expectedDirect = new PathInfoData("", 0L, direct, IslChangeType.DISCOVERED);

        assertEquals(expectedDirect, path.left);

        List<PathNode> reverse = new ArrayList<>();
        reverse.addAll(NetworkTopologyConstants.isl35.getPath());
        reverse.addAll(NetworkTopologyConstants.isl52.getPath());
        reverse.addAll(NetworkTopologyConstants.isl21.getPath());
        PathInfoData expectedReverse = new PathInfoData("", 0L, reverse, IslChangeType.DISCOVERED);

        assertEquals(expectedReverse, path.right);
    }

    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void getPathIntersection() throws Exception {
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl14);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl41);

        ImmutablePair<PathInfoData, PathInfoData> path1 = flowManager.getPath(
                NetworkTopologyConstants.sw4, NetworkTopologyConstants.sw3, 5);

        assertEquals(new LinkedList<>(Arrays.asList(NetworkTopologyConstants.isl45,
                NetworkTopologyConstants.isl53)), path1.left);
        assertEquals(new LinkedList<>(Arrays.asList(NetworkTopologyConstants.isl35,
                NetworkTopologyConstants.isl54)), path1.right);

        ImmutablePair<PathInfoData, PathInfoData> path2 = flowManager.getPath(
                NetworkTopologyConstants.sw2, NetworkTopologyConstants.sw3, 5);
        assertEquals(new LinkedList<>(Arrays.asList(NetworkTopologyConstants.isl25,
                NetworkTopologyConstants.isl53)), path2.left);
        assertEquals(new LinkedList<>(Arrays.asList(NetworkTopologyConstants.isl35,
                NetworkTopologyConstants.isl52)), path2.right);

        ImmutablePair<Set<PathNode>, Set<PathNode>> expected = new ImmutablePair<>(
                new HashSet<>(NetworkTopologyConstants.isl53.getPath()), new HashSet<>(NetworkTopologyConstants.isl35.getPath()));
        assertEquals(expected, flowManager.getPathIntersection(path1, path2));
        assertEquals(expected, flowManager.getPathIntersection(path2, path1));

        flowManager.getPath(NetworkTopologyConstants.sw5, NetworkTopologyConstants.sw3, 1);
    }

    @Test
    public void allocateFlow() throws Exception {
        flowManager.allocateFlow(new ImmutablePair<>(forwardCreatedFlow, reverseCreatedFlow));
        flowManager.allocateFlow(new ImmutablePair<>(forwardCreatedFlow, reverseCreatedFlow));

        Set<Integer> allocatedCookies = flowManager.resourceCache.getAllCookies();
        Set<Integer> allocatedVlanIds = flowManager.resourceCache.getAllVlanIds();
        Set<Integer> allocatedMeterIds = flowManager.resourceCache.getAllMeterIds(
                NetworkTopologyConstants.sw3.getSwitchId());

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

        Set<Integer> allocatedCookies = flowManager.resourceCache.getAllCookies();
        Set<Integer> allocatedVlanIds = flowManager.resourceCache.getAllVlanIds();
        Set<Integer> allocatedMeterIds = flowManager.resourceCache.getAllMeterIds(
                NetworkTopologyConstants.sw3.getSwitchId());

        assertEquals(Collections.emptySet(), allocatedCookies);
        assertEquals(Collections.emptySet(), allocatedVlanIds);
        assertEquals(Collections.emptySet(), allocatedMeterIds);
    }

    @Test
    public void eventTest() {
        FlowManager otherFlowManager = new FlowManager(storage, networkManager);
        Function<FlowManager.FlowChangeEvent, Void> switchChangeCallback =
                flowChangeEvent -> {
                    System.out.println(flowChangeEvent);
                    otherFlowManager.handleFlowChange(flowChangeEvent);
                    return null;
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
        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        networkManager.createSwitch(NetworkTopologyConstants.sw3);
        networkManager.createSwitch(NetworkTopologyConstants.sw4);
        networkManager.createSwitch(NetworkTopologyConstants.sw5);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl21);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl24);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl42);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl52);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl25);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl53);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl35);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl54);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl45);
    }
}
