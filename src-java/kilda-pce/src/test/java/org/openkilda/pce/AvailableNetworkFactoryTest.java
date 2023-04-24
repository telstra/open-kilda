/* Copyright 2021 Telstra Open Source
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

package org.openkilda.pce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory.BuildStrategy;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.Node;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.IslRepository.IslImmutableView;
import org.openkilda.persistence.repositories.RepositoryFactory;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AvailableNetworkFactoryTest {

    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    public static final String DIVERSE_GROUP_ID = "group_id_1";
    public static final long AVAILABLE_BANDWIDTH = 1000;
    public static final int SRC_PORT = 30;
    public static final int DEST_PORT = 31;
    public static final PathId FORWARD_PATH_ID = new PathId("path_id_1");
    public static final PathId REVERSE_PATH_ID = new PathId("path_id_2");
    public static final PathId PATH_ID_1 = new PathId("flow-path-id1");
    public static final PathId PATH_ID_2 = new PathId("flow-path-id2");
    public static final PathId PATH_ID_3 = new PathId("flow-path-id3");
    public static final Switch switchA = Switch.builder().switchId(SWITCH_ID_1).build();
    public static final Switch switchB = Switch.builder().switchId(SWITCH_ID_2).build();
    public static final Switch switchC = Switch.builder().switchId(SWITCH_ID_3).build();
    public static final Switch switchD = Switch.builder().switchId(SWITCH_ID_4).build();

    @Mock
    private PathComputerConfig config;
    @Mock
    RepositoryFactory repositoryFactory;
    @Mock
    private IslRepository islRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private HaFlowPathRepository haFlowPathRepository;

    private AvailableNetworkFactory availableNetworkFactory;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createHaFlowPathRepository()).thenReturn(haFlowPathRepository);

        availableNetworkFactory = new AvailableNetworkFactory(config, repositoryFactory);
    }

    @Test
    public void shouldBuildAvailableNetworkUsingCostStrategy() throws RecoverableException {
        Flow flow = getFlow(false);
        IslImmutableView isl = getIslView(flow);

        when(config.getNetworkStrategy()).thenReturn("COST");

        when(islRepository.findActiveByBandwidthAndEncapsulationType(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldIncreaseDiversityGroupUseCounter() throws RecoverableException {
        // Topology:
        // A----B----C     Already created flow: B-D
        //      |          Requested flow: A-B-C
        //      D

        // there is no ISL B-D because we assume that is has no enough bandwidth
        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchB, 3, switchC, 4));

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchB)
                .destSwitch(switchD)
                .pathId(FORWARD_PATH_ID)
                .segments(Collections.singletonList(PathSegment.builder().pathId(FORWARD_PATH_ID)
                        .srcSwitch(switchB).srcPort(SRC_PORT).destSwitch(switchD).destPort(DEST_PORT).build()))
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(java.util.Optional.of(forwardPath));

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchD)
                .destSwitch(switchB)
                .pathId(REVERSE_PATH_ID)
                .segments(Collections.singletonList(PathSegment.builder().pathId(REVERSE_PATH_ID)
                        .srcSwitch(switchD).srcPort(DEST_PORT).destSwitch(switchB).destPort(SRC_PORT).build()))
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow(false);
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchC);
        flow.setDiverseGroupId(DIVERSE_GROUP_ID);
        flow.setForwardPathId(new PathId("forward_path_id"));
        flow.setReversePathId(new PathId("reverse_path_id"));

        when(config.getNetworkStrategy()).thenReturn(BuildStrategy.COST.name());
        when(islRepository.findActiveByBandwidthAndEncapsulationType(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(isls);

        when(flowPathRepository.findPathIdsByFlowDiverseGroupId(DIVERSE_GROUP_ID))
                .thenReturn(Lists.newArrayList(FORWARD_PATH_ID, REVERSE_PATH_ID));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());
        assertEquals(2, availableNetwork.getSwitch(switchB.getSwitchId()).getDiversityGroupUseCounter());
    }

    @Test
    public void shouldBuildAvailableNetworkUsingCostStrategyWithIgnoreBandwidth() throws RecoverableException {
        Flow flow = getFlow(true);
        IslImmutableView isl = getIslView(flow);

        when(config.getNetworkStrategy()).thenReturn("COST");

        when(islRepository.findActiveByEncapsulationType(flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkUsingSymmetricCostStrategy() throws RecoverableException {
        Flow flow = getFlow(false);
        IslImmutableView isl = getIslView(flow);

        when(config.getNetworkStrategy()).thenReturn("SYMMETRIC_COST");

        when(islRepository.findSymmetricActiveByBandwidthAndEncapsulationType(flow.getBandwidth(),
                flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkUsingSymmetricCostStrategyWithIgnoreBandwidth() throws RecoverableException {
        Flow flow = getFlow(true);
        IslImmutableView isl = getIslView(flow);

        when(config.getNetworkStrategy()).thenReturn("SYMMETRIC_COST");

        when(islRepository.findActiveByEncapsulationType(flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkForFlowWithIgnoreBandwidthPaths() throws Exception {
        Flow flow = getFlow(false);
        IslImmutableView isl = getIslView(flow);
        PathId pathId = new PathId("flow-path-id");
        FlowPath flowPath = FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(flow.getSrcSwitch())
                .destSwitch(flow.getDestSwitch())
                .ignoreBandwidth(true)
                .build();

        when(config.getNetworkStrategy()).thenReturn("SYMMETRIC_COST");

        when(islRepository.findSymmetricActiveByBandwidthAndEncapsulationType(flow.getBandwidth(),
                flow.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl));
        when(flowPathRepository.findById(pathId)).thenReturn(Optional.of(flowPath));

        AvailableNetwork availableNetwork =
                availableNetworkFactory.getAvailableNetwork(flow, Collections.singletonList(pathId));

        assertAvailableNetworkIsCorrect(isl, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkReusingBandwidthFromYFlowAndReusePathResources() throws Exception {
        String yFlowId = "y-flow-id";
        Flow flow1 = Flow.builder()
                .flowId("test-id")
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .yFlowId(yFlowId)
                .build();

        Flow flow2 = Flow.builder()
                .flowId("test-id-2")
                .srcSwitch(switchB)
                .destSwitch(switchC)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .build();

        Flow flow3 = Flow.builder()
                .flowId("test-id-3")
                .srcSwitch(switchC)
                .destSwitch(switchD)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .build();

        IslImmutableView isl1 = getIslView(flow1.getSrcSwitch(), SRC_PORT, flow1.getDestSwitch(), DEST_PORT);
        IslImmutableView isl2 = getIslView(flow2.getSrcSwitch(), SRC_PORT, flow2.getDestSwitch(), DEST_PORT);
        IslImmutableView isl3 = getIslView(flow3.getSrcSwitch(), SRC_PORT, flow3.getDestSwitch(), DEST_PORT);

        FlowPath flowPath1 = FlowPath.builder()
                .pathId(PATH_ID_1)
                .srcSwitch(flow1.getSrcSwitch())
                .destSwitch(flow1.getDestSwitch())
                .build();
        FlowPath flowPath2 = FlowPath.builder()
                .pathId(PATH_ID_2)
                .srcSwitch(flow2.getSrcSwitch())
                .destSwitch(flow2.getDestSwitch())
                .build();
        FlowPath flowPath3 = FlowPath.builder()
                .pathId(PATH_ID_3)
                .srcSwitch(flow3.getSrcSwitch())
                .destSwitch(flow3.getDestSwitch())
                .build();


        when(config.getNetworkStrategy()).thenReturn("SYMMETRIC_COST");

        when(islRepository.findSymmetricActiveByBandwidthAndEncapsulationType(flow1.getBandwidth(),
                flow1.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl1));
        when(flowPathRepository.findById(PATH_ID_1)).thenReturn(Optional.of(flowPath1));
        when(flowPathRepository.findById(PATH_ID_2)).thenReturn(Optional.of(flowPath2));
        when(flowPathRepository.findById(PATH_ID_3)).thenReturn(Optional.of(flowPath3));


        when(flowPathRepository.findPathIdsBySharedBandwidthGroupId(yFlowId))
                .thenReturn(Collections.singleton(PATH_ID_2));
        when(islRepository.findActiveByPathAndBandwidthAndEncapsulationType(
                PATH_ID_2, flow2.getBandwidth(), flow2.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl2));
        when(islRepository.findActiveByPathAndBandwidthAndEncapsulationType(
                PATH_ID_3, flow3.getBandwidth(), flow3.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl3));

        AvailableNetwork availableNetwork =
                availableNetworkFactory.getAvailableNetwork(flow1, Collections.singletonList(PATH_ID_3));

        assertAvailableNetworkIsCorrect(isl1, availableNetwork);
        assertAvailableNetworkIsCorrect(isl2, availableNetwork);
        assertAvailableNetworkIsCorrect(isl3, availableNetwork);
    }

    @Test
    public void shouldBuildAvailableNetworkReusingOnlyReusePathResources() throws Exception {
        String yFlowId = "y-flow-id";
        Flow flow1 = Flow.builder()
                .flowId("test-id")
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .build();

        Flow flow2 = Flow.builder()
                .flowId("test-id-2")
                .srcSwitch(switchB)
                .destSwitch(switchC)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .build();

        Flow flow3 = Flow.builder()
                .flowId("test-id-3")
                .srcSwitch(switchC)
                .destSwitch(switchD)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .build();

        IslImmutableView isl1 = getIslView(flow1.getSrcSwitch(), SRC_PORT, flow1.getDestSwitch(), DEST_PORT);
        IslImmutableView isl2 = getIslView(flow2.getSrcSwitch(), SRC_PORT, flow2.getDestSwitch(), DEST_PORT);
        IslImmutableView isl3 = getIslView(flow3.getSrcSwitch(), SRC_PORT, flow3.getDestSwitch(), DEST_PORT);

        FlowPath flowPath1 = FlowPath.builder()
                .pathId(PATH_ID_1)
                .srcSwitch(flow1.getSrcSwitch())
                .destSwitch(flow1.getDestSwitch())
                .build();
        FlowPath flowPath2 = FlowPath.builder()
                .pathId(PATH_ID_2)
                .srcSwitch(flow2.getSrcSwitch())
                .destSwitch(flow2.getDestSwitch())
                .build();
        FlowPath flowPath3 = FlowPath.builder()
                .pathId(PATH_ID_3)
                .srcSwitch(flow3.getSrcSwitch())
                .destSwitch(flow3.getDestSwitch())
                .build();


        when(config.getNetworkStrategy()).thenReturn("SYMMETRIC_COST");

        when(islRepository.findSymmetricActiveByBandwidthAndEncapsulationType(flow1.getBandwidth(),
                flow1.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl1));
        when(flowPathRepository.findById(PATH_ID_1)).thenReturn(Optional.of(flowPath1));
        when(flowPathRepository.findById(PATH_ID_2)).thenReturn(Optional.of(flowPath2));
        when(flowPathRepository.findById(PATH_ID_3)).thenReturn(Optional.of(flowPath3));


        when(flowPathRepository.findPathIdsBySharedBandwidthGroupId(yFlowId))
                .thenReturn(Collections.singleton(PATH_ID_2));
        when(islRepository.findActiveByPathAndBandwidthAndEncapsulationType(
                PATH_ID_2, flow2.getBandwidth(), flow2.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl2));
        when(islRepository.findActiveByPathAndBandwidthAndEncapsulationType(
                PATH_ID_3, flow3.getBandwidth(), flow3.getEncapsulationType()))
                .thenReturn(Collections.singletonList(isl3));

        AvailableNetwork availableNetwork =
                availableNetworkFactory.getAvailableNetwork(flow1, Collections.singletonList(PATH_ID_3));

        assertAvailableNetworkIsCorrect(isl1, availableNetwork);
        assertAvailableNetworkIsCorrect(isl3, availableNetwork);

        Node src = availableNetwork.getSwitch(isl2.getSrcSwitchId());
        assertNotNull(src);
        assertEquals(0, src.getOutgoingLinks().size());

        Node dst = availableNetwork.getSwitch(isl2.getDestSwitchId());
        assertNotNull(dst);
        assertEquals(0, dst.getIncomingLinks().size());
    }

    private static Flow getFlow(boolean ignoreBandwidth) {
        return Flow.builder()
                .flowId("test-id")
                .srcSwitch(Switch.builder().switchId(new SwitchId("1")).build())
                .destSwitch(Switch.builder().switchId(new SwitchId("2")).build())
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .ignoreBandwidth(ignoreBandwidth)
                .build();
    }

    private static IslImmutableView getIslView(Flow flow) {
        return getIslView(flow.getSrcSwitch(), SRC_PORT, flow.getDestSwitch(), DEST_PORT);
    }

    private static IslImmutableView getIslView(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort) {
        IslImmutableView isl = mock(IslImmutableView.class);
        when(isl.getSrcSwitchId()).thenReturn(srcSwitch.getSwitchId());
        when(isl.getSrcPort()).thenReturn(srcPort);
        when(isl.getDestSwitchId()).thenReturn(dstSwitch.getSwitchId());
        when(isl.getDestPort()).thenReturn(dstPort);
        when(isl.getCost()).thenReturn(10);
        when(isl.getLatency()).thenReturn(33L);
        when(isl.getAvailableBandwidth()).thenReturn(AVAILABLE_BANDWIDTH);
        when(isl.isUnderMaintenance()).thenReturn(false);
        when(isl.isUnstable()).thenReturn(false);
        return isl;
    }

    private static List<IslImmutableView> getBidirectionalIsls(
            Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort) {
        return Lists.newArrayList(
                getIslView(srcSwitch, srcPort, dstSwitch, dstPort),
                getIslView(dstSwitch, dstPort, srcSwitch, srcPort));
    }

    private static void assertAvailableNetworkIsCorrect(IslImmutableView isl, AvailableNetwork availableNetwork) {
        Node src = availableNetwork.getSwitch(isl.getSrcSwitchId());
        assertNotNull(src);
        assertEquals(1, src.getOutgoingLinks().size());
        Edge edge = src.getOutgoingLinks().iterator().next();
        assertEquals(isl.getSrcSwitchId(), edge.getSrcSwitch().getSwitchId());
        assertEquals(isl.getSrcPort(), edge.getSrcPort());
        assertEquals(isl.getDestSwitchId(), edge.getDestSwitch().getSwitchId());
        assertEquals(isl.getDestPort(), edge.getDestPort());
        assertEquals(isl.getCost(), edge.getCost());
        assertEquals(isl.getLatency(), edge.getLatency());
        assertEquals(isl.getAvailableBandwidth(), edge.getAvailableBandwidth());
        Node dst = availableNetwork.getSwitch(isl.getDestSwitchId());
        assertNotNull(dst);
        assertEquals(1, dst.getIncomingLinks().size());
    }

}
