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

package org.openkilda.pce.cache;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.error.CacheException;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.NetworkTopologyConstants;
import org.openkilda.pce.provider.PathComputer;
import org.openkilda.pce.provider.PathComputerMock;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowCacheTest {
    private final NetworkCache networkCache = new NetworkCache();
    private final PathComputer computer = new PathComputerMock().withNetwork(networkCache.getNetwork());
    private final FlowCache flowCache = new FlowCache();
    private final PathComputer.Strategy defaultStrategy = PathComputer.Strategy.COST;

    private final Flow firstFlow = new Flow("first-flow", 1, false, "first-flow",
            new SwitchId("ff:01"), 11, 100,
            new SwitchId("ff:03"), 11, 200);
    private final Flow secondFlow = new Flow("second-flow", 1, false, "second-flow",
            new SwitchId("ff:05"), 12, 100,
            new SwitchId("ff:03"), 12, 200);
    private final Flow thirdFlow = new Flow("third-flow", 1, false, "third-flow",
            new SwitchId("ff:03"), 21, 100,
            new SwitchId("ff:03"), 22, 200);
    private final Flow fourthFlow = new Flow("fourth-flow", 1, false, "fourth-flow",
            new SwitchId("ff:04"), 21, 100,
            new SwitchId("ff:04"), 22, 200);
    private final Flow fifthFlow = new Flow("fifth-flow", 1, false, "fifth-flow",
            new SwitchId("ff:05"), 21, 100,
            new SwitchId("ff:05"), 22, 200);

    private final Flow noBandwidthFlow = new Flow("no-bandwidth-flow", 0, true, "no-bandwidth-flow",
            new SwitchId("ff:01"), 11, 100,
            new SwitchId("ff:03"), 11, 200);

    @Before
    public void setUp() {
        buildNetworkTopology(networkCache);
    }

    @After
    public void tearDown() {
        networkCache.clear();
        flowCache.clear();
    }

    @Test(expected = CacheException.class)
    public void getNotExistentFlow() throws Exception {
        flowCache.getFlow(firstFlow.getFlowId());
    }

    @Test
    public void getFlow() throws Exception {
        FlowPair<Flow, Flow> newFlow =
                flowCache.createFlow(firstFlow, computer.getPath(firstFlow, defaultStrategy));
        FlowPair<Flow, Flow> storedFlow = flowCache.getFlow(firstFlow.getFlowId());
        assertEquals(newFlow, storedFlow);
    }

    @Test
    public void createFlow() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(firstFlow, defaultStrategy);
        FlowPair<Flow, Flow> newFlow = flowCache.createFlow(firstFlow, path);

        Flow forward = newFlow.left;
        assertEquals(1 | ResourceCache.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(2, forward.getTransitVlan());
        assertEquals(ResourceCache.MIN_METER_ID, forward.getMeterId());
        assertEquals(path.getLeft(), forward.getFlowPath());

        Flow reverse = newFlow.right;
        assertEquals(1 | ResourceCache.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(3, reverse.getTransitVlan());
        assertEquals(ResourceCache.MIN_METER_ID, reverse.getMeterId());
        assertEquals(path.getRight(), reverse.getFlowPath());

        assertEquals(1, flowCache.dumpFlows().size());
    }

    @Test
    public void createNoBandwidthFlow() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(noBandwidthFlow, defaultStrategy);
        FlowPair<Flow, Flow> newFlow = flowCache.createFlow(noBandwidthFlow, path);

        Flow forward = newFlow.left;
        assertEquals(1 | ResourceCache.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(2, forward.getTransitVlan());
        assertEquals(0, forward.getMeterId());
        assertEquals(path.getLeft(), forward.getFlowPath());

        Flow reverse = newFlow.right;
        assertEquals(1 | ResourceCache.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(3, reverse.getTransitVlan());
        assertEquals(0, reverse.getMeterId());
        assertEquals(path.getRight(), reverse.getFlowPath());

        assertEquals(1, flowCache.dumpFlows().size());
    }

    @Test
    public void createSingleSwitchFlows() throws Exception {
        /*
         * This is to validate that single switch flows don't consume transit vlans.
         */
        FlowPair<PathInfoData, PathInfoData> path1 = computer.getPath(thirdFlow, defaultStrategy);
        FlowPair<PathInfoData, PathInfoData> path2 = computer.getPath(fourthFlow, defaultStrategy);
        FlowPair<PathInfoData, PathInfoData> path3 = computer.getPath(fifthFlow, defaultStrategy);

        for (int i = 0; i < 1000; i++) {
            thirdFlow.setFlowId(thirdFlow.getFlowId() + i);
            fourthFlow.setFlowId(fourthFlow.getFlowId() + i);
            fifthFlow.setFlowId(fifthFlow.getFlowId() + i);
            flowCache.createFlow(thirdFlow, path1);
            flowCache.createFlow(fourthFlow, path2);
            flowCache.createFlow(fifthFlow, path3);
        }

    }

    @Test
    public void createAlreadyExistentFlow() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(firstFlow, defaultStrategy);
        flowCache.createFlow(firstFlow, path);

        final Set<Integer> allocatedVlans = flowCache.getAllocatedVlans();
        final Set<Integer> allocatedCookies = flowCache.getAllocatedCookies();
        final Map<SwitchId, Set<Integer>> allocatedMeters = flowCache.getAllocatedMeters();
        try {
            flowCache.createFlow(firstFlow, path);
            throw new AssertionError(String.format(
                    "Expected exception %s is not raised", CacheException.class.getName()));
        } catch (CacheException e) {
            // all good till now
        }

        String callName = String.format("%s.createFlow(...) call", flowCache.getClass().getCanonicalName());
        Assert.assertEquals(
                String.format("Detect VlanId leak in %s", callName),
                allocatedVlans, flowCache.getAllocatedVlans());
        Assert.assertEquals(
                String.format("Detect cookies leak in %s", callName),
                allocatedCookies, flowCache.getAllocatedCookies());
        Assert.assertEquals(
                String.format("Detect meterId leak in %s", callName),
                allocatedMeters, flowCache.getAllocatedMeters());
    }

    @Test
    public void deleteFlow() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(firstFlow, defaultStrategy);
        FlowPair<Flow, Flow> newFlow = flowCache.createFlow(firstFlow, path);
        FlowPair<Flow, Flow> oldFlow = flowCache.deleteFlow(firstFlow.getFlowId());
        assertEquals(newFlow, oldFlow);
        assertEquals(0, flowCache.dumpFlows().size());
    }

    @Test
    public void updateFlow() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(firstFlow, defaultStrategy);

        final FlowPair<Flow, Flow> oldFlow = flowCache.createFlow(firstFlow, path);
        assertEquals(1, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:01")).size());
        assertEquals(0, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:02")).size());
        assertEquals(1, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:03")).size());
        assertEquals(0, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:04")).size());
        assertEquals(0, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:05")).size());
        assertEquals(2, flowCache.resourceCache.getAllVlanIds().size());
        assertEquals(1, flowCache.resourceCache.getAllCookies().size());

        final FlowPair<Flow, Flow> newFlow = flowCache.updateFlow(firstFlow, path);
        assertEquals(1, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:01")).size());
        assertEquals(0, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:02")).size());
        assertEquals(1, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:03")).size());
        assertEquals(0, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:04")).size());
        assertEquals(0, flowCache.resourceCache.getAllMeterIds(new SwitchId("ff:05")).size());
        assertEquals(2, flowCache.resourceCache.getAllVlanIds().size());
        assertEquals(1, flowCache.resourceCache.getAllCookies().size());

        assertEquals(1, flowCache.dumpFlows().size());

        // If two objects are equal according to the equals(Object) method, then calling the
        // hashCode method on each of the two objects must produce the same integer result.
        // from https://docs.oracle.com/javase/7/docs/api/java/lang/Object.html#hashCode()
        // assertNotEquals(newFlow.hashCode(), oldFlow.hashCode());
        assertEquals(newFlow.hashCode(), oldFlow.hashCode());
        assertEquals(oldFlow, newFlow);
    }

    @Test
    public void updateNoBandwidthFlow() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(firstFlow, defaultStrategy);

        FlowPair<Flow, Flow> oldFlow = flowCache.createFlow(firstFlow, path);

        assertEquals(ResourceCache.MIN_METER_ID, oldFlow.left.getMeterId());
        assertEquals(ResourceCache.MIN_METER_ID, oldFlow.right.getMeterId());

        firstFlow.setBandwidth(0);
        fifthFlow.setIgnoreBandwidth(true);

        FlowPair<Flow, Flow> newFlow = flowCache.updateFlow(firstFlow, path);

        assertEquals(0, newFlow.left.getMeterId());
        assertEquals(0, newFlow.right.getMeterId());
    }

    @Test
    public void updateMissingFlow() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(firstFlow, defaultStrategy);

        final Set<Integer> allocatedVlans = flowCache.getAllocatedVlans();
        final Set<Integer> allocatedCookies = flowCache.getAllocatedCookies();
        final Map<SwitchId, Set<Integer>> allocatedMeters = flowCache.getAllocatedMeters();

        try {
            flowCache.updateFlow(firstFlow, path);
            throw new AssertionError(String.format(
                    "Expected exception %s is not raised", CacheException.class.getName()));
        } catch (CacheException e) {
            // all good till now
        }

        String callName = String.format("%s.createFlow(...) call", flowCache.getClass().getCanonicalName());
        Assert.assertEquals(
                String.format("Detect VlanId leak in %s", callName),
                allocatedVlans, flowCache.getAllocatedVlans());
        Assert.assertEquals(
                String.format("Detect cookies leak in %s", callName),
                allocatedCookies, flowCache.getAllocatedCookies());
        Assert.assertEquals(
                String.format("Detect meterId leak in %s", callName),
                allocatedMeters, flowCache.getAllocatedMeters());
    }

    @Test
    public void dumpFlows() throws Exception {
        FlowPair<Flow, Flow> first = flowCache.createFlow(firstFlow, computer.getPath(firstFlow, defaultStrategy));
        FlowPair<Flow, Flow> second =
                flowCache.createFlow(secondFlow, computer.getPath(secondFlow, defaultStrategy));
        FlowPair<Flow, Flow> third = flowCache.createFlow(thirdFlow, computer.getPath(thirdFlow, defaultStrategy));
        assertEquals(new HashSet<>(Arrays.asList(first, second, third)), flowCache.dumpFlows());
    }

    @Test
    public void getFlowPath() throws Exception {
        flowCache.createFlow(firstFlow, computer.getPath(firstFlow, defaultStrategy));
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(firstFlow, defaultStrategy);
        assertEquals(path, flowCache.getFlowPath(firstFlow.getFlowId()));
    }

    @Test
    public void getFlowsWithAffectedPathBySwitch() throws Exception {
        Set<FlowPair<Flow, Flow>> affected;
        final FlowPair<Flow, Flow> first =
                flowCache.createFlow(firstFlow, computer.getPath(firstFlow, defaultStrategy));
        final FlowPair<Flow, Flow> second =
                flowCache.createFlow(secondFlow, computer.getPath(secondFlow, defaultStrategy));
        final FlowPair<Flow, Flow> third =
                flowCache.createFlow(thirdFlow, computer.getPath(thirdFlow, defaultStrategy));

        affected = flowCache.getFlowsWithAffectedPath(NetworkTopologyConstants.sw5.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);

        affected = flowCache.getFlowsWithAffectedPath(NetworkTopologyConstants.sw3.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(first, second, third)), affected);

        affected = flowCache.getFlowsWithAffectedPath(NetworkTopologyConstants.sw1.getSwitchId());
        assertEquals(Collections.singleton(first), affected);
    }

    @Test
    public void getFlowsWithAffectedPathByIsl() throws Exception {
        Set<FlowPair<Flow, Flow>> affected;
        final FlowPair<Flow, Flow> first =
                flowCache.createFlow(firstFlow, computer.getPath(firstFlow, defaultStrategy));
        final FlowPair<Flow, Flow> second =
                flowCache.createFlow(secondFlow, computer.getPath(secondFlow, defaultStrategy));
        flowCache.createFlow(thirdFlow, computer.getPath(thirdFlow, defaultStrategy));

        affected = flowCache.getFlowsWithAffectedPath(NetworkTopologyConstants.isl12);
        assertEquals(Collections.singleton(first), affected);

        affected = flowCache.getFlowsWithAffectedPath(NetworkTopologyConstants.isl21);
        assertEquals(Collections.singleton(first), affected);

        affected = flowCache.getFlowsWithAffectedPath(NetworkTopologyConstants.isl53);
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);

        affected = flowCache.getFlowsWithAffectedPath(NetworkTopologyConstants.isl35);
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);
    }

    @Test
    public void getFlowsWithAffectedPathByPort() throws Exception {
        Set<FlowPair<Flow, Flow>> affected;
        final FlowPair<Flow, Flow> first =
                flowCache.createFlow(firstFlow, computer.getPath(firstFlow, defaultStrategy));
        final FlowPair<Flow, Flow> second =
                flowCache.createFlow(secondFlow, computer.getPath(secondFlow, defaultStrategy));
        flowCache.createFlow(thirdFlow, computer.getPath(thirdFlow, defaultStrategy));

        affected = flowCache.getFlowsWithAffectedPath(new PortInfoData(
                NetworkTopologyConstants.isl12.getPath().get(0)));
        assertEquals(Collections.singleton(first), affected);

        affected = flowCache.getFlowsWithAffectedPath(new PortInfoData(
                NetworkTopologyConstants.isl21.getPath().get(0)));
        assertEquals(Collections.singleton(first), affected);

        affected = flowCache.getFlowsWithAffectedPath(new PortInfoData(
                NetworkTopologyConstants.isl53.getPath().get(0)));
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);

        affected = flowCache.getFlowsWithAffectedPath(new PortInfoData(
                NetworkTopologyConstants.isl35.getPath().get(0)));
        assertEquals(new HashSet<>(Arrays.asList(first, second)), affected);
    }

    @Test
    public void getFlowsForUpState() throws Exception {
        Map<String, String> affected;
        final FlowPair<Flow, Flow> first =
                flowCache.createFlow(firstFlow, computer.getPath(firstFlow, defaultStrategy));
        final FlowPair<Flow, Flow> second =
                flowCache.createFlow(secondFlow, computer.getPath(secondFlow, defaultStrategy));
        final FlowPair<Flow, Flow> third =
                flowCache.createFlow(thirdFlow, computer.getPath(thirdFlow, defaultStrategy));

        affected = flowCache.getFlowsWithAffectedEndpoint(NetworkTopologyConstants.sw5.getSwitchId());
        assertEquals(Collections.singleton(second.getLeft().getFlowId()), affected.keySet());

        affected = flowCache.getFlowsWithAffectedEndpoint(NetworkTopologyConstants.sw3.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(first.getLeft().getFlowId(),
                second.getLeft().getFlowId(), third.getLeft().getFlowId())), affected.keySet());

        affected = flowCache.getFlowsWithAffectedEndpoint(NetworkTopologyConstants.sw1.getSwitchId());
        assertEquals(Collections.singleton(first.getLeft().getFlowId()), affected.keySet());
    }

    @Test
    public void getPath() throws Exception {
        FlowPair<PathInfoData, PathInfoData> path = computer.getPath(
                makeFlow("generic", NetworkTopologyConstants.sw1, NetworkTopologyConstants.sw3, 0), defaultStrategy);
        System.out.println(path.toString());

        PathNode node;

        List<PathNode> direct = new ArrayList<>();

        node = new PathNode(NetworkTopologyConstants.isl12.getPath().get(0));
        node.setSeqId(0);
        direct.add(node);

        node = new PathNode(NetworkTopologyConstants.isl12.getPath().get(1));
        node.setSeqId(1);
        direct.add(node);

        node = new PathNode(NetworkTopologyConstants.isl25.getPath().get(0));
        node.setSeqId(2);
        direct.add(node);

        node = new PathNode(NetworkTopologyConstants.isl25.getPath().get(1));
        node.setSeqId(3);
        direct.add(node);

        node = new PathNode(NetworkTopologyConstants.isl53.getPath().get(0));
        node.setSeqId(4);
        direct.add(node);

        node = new PathNode(NetworkTopologyConstants.isl53.getPath().get(1));
        node.setSeqId(5);
        direct.add(node);

        PathInfoData expectedDirect = new PathInfoData(
                NetworkTopologyConstants.isl12.getLatency()
                        + NetworkTopologyConstants.isl25.getLatency()
                        + NetworkTopologyConstants.isl53.getLatency(),
                direct);

        assertEquals(expectedDirect, path.getLeft());

        List<PathNode> reverse = new ArrayList<>();

        node = new PathNode(NetworkTopologyConstants.isl35.getPath().get(0));
        node.setSeqId(0);
        reverse.add(node);

        node = new PathNode(NetworkTopologyConstants.isl35.getPath().get(1));
        node.setSeqId(1);
        reverse.add(node);

        node = new PathNode(NetworkTopologyConstants.isl52.getPath().get(0));
        node.setSeqId(2);
        reverse.add(node);

        node = new PathNode(NetworkTopologyConstants.isl52.getPath().get(1));
        node.setSeqId(3);
        reverse.add(node);

        node = new PathNode(NetworkTopologyConstants.isl21.getPath().get(0));
        node.setSeqId(4);
        reverse.add(node);

        node = new PathNode(NetworkTopologyConstants.isl21.getPath().get(1));
        node.setSeqId(5);
        reverse.add(node);

        PathInfoData expectedReverse = new PathInfoData(
                NetworkTopologyConstants.isl12.getLatency()
                        + NetworkTopologyConstants.isl25.getLatency()
                        + NetworkTopologyConstants.isl53.getLatency(),
                reverse);

        assertEquals(expectedReverse, path.getRight());
    }

    @Test
    public void getPathIntersection() throws Exception {
        networkCache.createIsl(NetworkTopologyConstants.isl14);
        networkCache.createIsl(NetworkTopologyConstants.isl41);

        PathNode node;

        FlowPair<PathInfoData, PathInfoData> path43 = computer.getPath(
                makeFlow("collide-flow-one", NetworkTopologyConstants.sw4, NetworkTopologyConstants.sw3, 5),
                defaultStrategy);
        System.out.println(path43);

        List<PathNode> nodesForward43 = new ArrayList<>();

        node = new PathNode(NetworkTopologyConstants.isl45.getPath().get(0));
        node.setSeqId(0);
        nodesForward43.add(node);

        node = new PathNode(NetworkTopologyConstants.isl45.getPath().get(1));
        node.setSeqId(1);
        nodesForward43.add(node);

        node = new PathNode(NetworkTopologyConstants.isl53.getPath().get(0));
        node.setSeqId(2);
        nodesForward43.add(node);

        node = new PathNode(NetworkTopologyConstants.isl53.getPath().get(1));
        node.setSeqId(3);
        nodesForward43.add(node);

        final PathInfoData islForwardPath43 = new PathInfoData(
                NetworkTopologyConstants.isl45.getLatency() + NetworkTopologyConstants.isl53.getLatency(),
                nodesForward43);

        List<PathNode> nodesReverse43 = new ArrayList<>();

        node = new PathNode(NetworkTopologyConstants.isl35.getPath().get(0));
        node.setSeqId(0);
        nodesReverse43.add(node);

        node = new PathNode(NetworkTopologyConstants.isl35.getPath().get(1));
        node.setSeqId(1);
        nodesReverse43.add(node);

        node = new PathNode(NetworkTopologyConstants.isl54.getPath().get(0));
        node.setSeqId(2);
        nodesReverse43.add(node);

        node = new PathNode(NetworkTopologyConstants.isl54.getPath().get(1));
        node.setSeqId(3);
        nodesReverse43.add(node);

        PathInfoData islReversePath43 = new PathInfoData(
                NetworkTopologyConstants.isl35.getLatency() + NetworkTopologyConstants.isl54.getLatency(),
                nodesReverse43);

        assertEquals(islForwardPath43, path43.left);
        assertEquals(islReversePath43, path43.right);

        FlowPair<PathInfoData, PathInfoData> path23 = computer.getPath(
                makeFlow("collide-flow-two", NetworkTopologyConstants.sw2, NetworkTopologyConstants.sw3, 5),
                defaultStrategy);
        System.out.println(path23);

        List<PathNode> nodesForward23 = new ArrayList<>();

        node = new PathNode(NetworkTopologyConstants.isl25.getPath().get(0));
        node.setSeqId(0);
        nodesForward23.add(node);

        node = new PathNode(NetworkTopologyConstants.isl25.getPath().get(1));
        node.setSeqId(1);
        nodesForward23.add(node);

        PathNode node1 = new PathNode(NetworkTopologyConstants.isl53.getPath().get(0));
        node1.setSeqId(2);
        nodesForward23.add(node1);

        PathNode node2 = new PathNode(NetworkTopologyConstants.isl53.getPath().get(1));
        node2.setSeqId(3);
        nodesForward23.add(node2);

        final PathInfoData islForwardPath23 = new PathInfoData(
                NetworkTopologyConstants.isl25.getLatency() + NetworkTopologyConstants.isl53.getLatency(),
                nodesForward23);

        List<PathNode> nodesReverse23 = new ArrayList<>();

        PathNode node3 = new PathNode(NetworkTopologyConstants.isl35.getPath().get(0));
        node3.setSeqId(0);
        nodesReverse23.add(node3);

        PathNode node4 = new PathNode(NetworkTopologyConstants.isl35.getPath().get(1));
        node4.setSeqId(1);
        nodesReverse23.add(node4);

        node = new PathNode(NetworkTopologyConstants.isl52.getPath().get(0));
        node.setSeqId(2);
        nodesReverse23.add(node);

        node = new PathNode(NetworkTopologyConstants.isl52.getPath().get(1));
        node.setSeqId(3);
        nodesReverse23.add(node);

        PathInfoData islReversePath23 = new PathInfoData(
                NetworkTopologyConstants.isl35.getLatency() + NetworkTopologyConstants.isl52.getLatency(),
                nodesReverse23);

        assertEquals(islForwardPath23, path23.left);
        assertEquals(islReversePath23, path23.right);

        FlowPair<Set<PathNode>, Set<PathNode>> expected = new FlowPair<>(
                new HashSet<>(Arrays.asList(node1, node2)),
                new HashSet<>(Arrays.asList(node3, node4)));

        assertEquals(expected, flowCache.getPathIntersection(path43, path23));
        assertEquals(expected, flowCache.getPathIntersection(path23, path43));
    }

    @Test(expected = CacheException.class)
    public void getPathWithNoEnoughAvailableBandwidth() throws Exception {
        getPathIntersection();
        System.out.println(networkCache.dumpIsls());
        computer.getPath(
                makeFlow("bandwidth-overflow", NetworkTopologyConstants.sw5, NetworkTopologyConstants.sw3, 1),
                defaultStrategy);
    }

    private void buildNetworkTopology(NetworkCache networkCache) {
        networkCache.createSwitch(NetworkTopologyConstants.sw1);
        networkCache.createSwitch(NetworkTopologyConstants.sw2);
        networkCache.createSwitch(NetworkTopologyConstants.sw3);
        networkCache.createSwitch(NetworkTopologyConstants.sw4);
        networkCache.createSwitch(NetworkTopologyConstants.sw5);
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl12));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl21));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl24));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl42));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl52));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl25));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl53));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl35));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl54));
        networkCache.createOrUpdateIsl(new IslInfoData(NetworkTopologyConstants.isl45));
    }

    private Flow makeFlow(String id, SwitchInfoData source, SwitchInfoData dest, int bandwidth) {
        Flow flow = new Flow();

        flow.setFlowId(id);
        flow.setSourceSwitch(source.getSwitchId());
        flow.setDestinationSwitch(dest.getSwitchId());
        flow.setBandwidth(bandwidth);

        return flow;
    }
}
