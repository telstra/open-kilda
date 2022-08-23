/* Copyright 2022 Telstra Open Source
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

package org.openkilda.pce.finder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.AvailableNetworkFactory.BuildStrategy;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.impl.InMemoryPathComputer;
import org.openkilda.persistence.repositories.FlowPathRepository;
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

public class ProtectedPathFinderTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    private static final SwitchId SWITCH_ID_5 = new SwitchId(5);
    private static final SwitchId SWITCH_ID_6 = new SwitchId(6);
    private static final SwitchId SWITCH_ID_7 = new SwitchId(7);
    private static final Switch switchA = Switch.builder().switchId(SWITCH_ID_1).build();
    private static final Switch switchB = Switch.builder().switchId(SWITCH_ID_2).build();
    private static final Switch switchC = Switch.builder().switchId(SWITCH_ID_3).build();
    private static final Switch switchD = Switch.builder().switchId(SWITCH_ID_4).build();
    private static final Switch switchE = Switch.builder().switchId(SWITCH_ID_5).build();
    private static final Switch switchG = Switch.builder().switchId(SWITCH_ID_6).build();
    private static final Switch switchF = Switch.builder().switchId(SWITCH_ID_7).build();
    private static final PathId FORWARD_PATH_ID = new PathId("forward_path_1");
    private static final PathId REVERSE_PATH_ID = new PathId("reverse_path_1");
    private static final String DIVERSE_GROUP_ID = "group_id_1";
    private static final PathComputationStrategy PATH_COMPUTATION_STRATEGY = PathComputationStrategy.MAX_LATENCY;

    @Mock
    private PathComputerConfig config;
    @Mock
    private RepositoryFactory repositoryFactory;
    @Mock
    private IslRepository islRepository;
    @Mock
    private FlowPathRepository flowPathRepository;

    private AvailableNetworkFactory availableNetworkFactory;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(config.getDiversitySwitchLatency()).thenReturn(10000L);
        when(config.getDiversityIslLatency()).thenReturn(10000L);
        when(config.getDiversityIslCost()).thenReturn(10000);
        when(config.getDiversitySwitchCost()).thenReturn(10000);
        when(config.getDiversityPopIslCost()).thenReturn(10000);

        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);

        availableNetworkFactory = new AvailableNetworkFactory(config, repositoryFactory);
    }

    @Test
    public void shouldNotFindProtectedPath() throws RecoverableException, UnroutableFlowException {
        // Topology:
        // A----B----C     Already created flow: A-B-C
        //                 No protected path here for this flow

        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchB, 3, switchC, 4));

        List<PathSegment> forwardSegments = new ArrayList<>();
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchA).srcPort(10)
                .destSwitch(switchB).destPort(10).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchB).srcPort(10)
                .destSwitch(switchC).destPort(10).build());

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .pathId(FORWARD_PATH_ID)
                .segments(forwardSegments)
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(java.util.Optional.of(forwardPath));


        List<PathSegment> reverseSegments = new ArrayList<>();
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchC).srcPort(10)
                .destSwitch(switchB).destPort(10).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchB).srcPort(10)
                .destSwitch(switchA).destPort(10).build());

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .pathId(REVERSE_PATH_ID)
                .segments(reverseSegments)
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow(switchA, switchC);
        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);

        when(config.getNetworkStrategy()).thenReturn(BuildStrategy.COST.name());
        when(islRepository.findActiveByBandwidthAndEncapsulationType(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(isls);

        when(flowPathRepository.findPathIdsByFlowDiverseGroupId(DIVERSE_GROUP_ID))
                .thenReturn(Lists.newArrayList(FORWARD_PATH_ID, REVERSE_PATH_ID));

        // check diversity counter
        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());
        assertEquals(2, availableNetwork.getSwitch(switchB.getSwitchId()).getDiversityGroupUseCounter());

        // check found path
        PathComputer pathComputer = new InMemoryPathComputer(
                availableNetworkFactory, new BestWeightAndShortestPathFinder(200), config);

        assertThrows(UnroutableFlowException.class, () -> pathComputer.getPath(flow, Collections.emptyList(), true));
    }

    @Test
    public void shouldNotFindAnyProtectedPath() throws RecoverableException, UnroutableFlowException {
        // Topology:
        //      D----E
        //      |    |
        // A----B----C     Already created flow: A-B-C
        //                 No protected path here for this flow

        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchB, 3, switchC, 4));
        isls.addAll(getBidirectionalIsls(switchB, 7, switchD, 8));
        isls.addAll(getBidirectionalIsls(switchD, 5, switchE, 6));
        isls.addAll(getBidirectionalIsls(switchE, 9, switchC, 11));

        List<PathSegment> forwardSegments = new ArrayList<>();
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchA).srcPort(1)
                .destSwitch(switchB).destPort(2).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchB).srcPort(3)
                .destSwitch(switchC).destPort(4).build());

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .pathId(FORWARD_PATH_ID)
                .segments(forwardSegments)
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(java.util.Optional.of(forwardPath));


        List<PathSegment> reverseSegments = new ArrayList<>();
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchC).srcPort(4)
                .destSwitch(switchB).destPort(3).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchB).srcPort(3)
                .destSwitch(switchA).destPort(1).build());

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .pathId(REVERSE_PATH_ID)
                .segments(reverseSegments)
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow(switchA, switchC);
        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);

        when(config.getNetworkStrategy()).thenReturn(BuildStrategy.COST.name());
        when(islRepository.findActiveByBandwidthAndEncapsulationType(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(isls);

        when(flowPathRepository.findPathIdsByFlowDiverseGroupId(DIVERSE_GROUP_ID))
                .thenReturn(Lists.newArrayList(FORWARD_PATH_ID, REVERSE_PATH_ID));

        // check diversity counter
        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());
        assertEquals(2, availableNetwork.getSwitch(switchB.getSwitchId()).getDiversityGroupUseCounter());

        // check found path
        PathComputer pathComputer = new InMemoryPathComputer(
                availableNetworkFactory, new BestWeightAndShortestPathFinder(200), config);

        assertThrows(UnroutableFlowException.class, () -> pathComputer
                .getPath(flow, Collections.emptyList(), true));
    }

    @Test
    public void shouldFindProtectedPath() throws RecoverableException, UnroutableFlowException {
        // Topology:
        //     D-----E
        //   /       |
        // A----B----C     Already created flow: A-B-C
        //                 Expected protected path: A-D-E-C

        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchB, 3, switchC, 4));
        isls.addAll(getBidirectionalIsls(switchA, 7, switchD, 8));
        isls.addAll(getBidirectionalIsls(switchD, 5, switchE, 6));
        isls.addAll(getBidirectionalIsls(switchE, 9, switchC, 11));

        List<PathSegment> forwardSegments = new ArrayList<>();
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchA).srcPort(1)
                .destSwitch(switchB).destPort(2).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchB).srcPort(3)
                .destSwitch(switchC).destPort(4).build());

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .pathId(FORWARD_PATH_ID)
                .segments(forwardSegments)
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(java.util.Optional.of(forwardPath));


        List<PathSegment> reverseSegments = new ArrayList<>();
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchC).srcPort(4)
                .destSwitch(switchB).destPort(3).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchB).srcPort(3)
                .destSwitch(switchA).destPort(1).build());

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .pathId(REVERSE_PATH_ID)
                .segments(reverseSegments)
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow(switchA, switchC);
        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);

        when(config.getNetworkStrategy()).thenReturn(BuildStrategy.COST.name());
        when(islRepository.findActiveByBandwidthAndEncapsulationType(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(isls);

        when(flowPathRepository.findPathIdsByFlowDiverseGroupId(DIVERSE_GROUP_ID))
                .thenReturn(Lists.newArrayList(FORWARD_PATH_ID, REVERSE_PATH_ID));

        // check diversity counter
        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());
        assertEquals(2, availableNetwork.getSwitch(switchB.getSwitchId()).getDiversityGroupUseCounter());

        // check found path
        PathComputer pathComputer = new InMemoryPathComputer(
                availableNetworkFactory, new BestWeightAndShortestPathFinder(200), config);

        GetPathsResult path = pathComputer.getPath(flow, Collections.emptyList(), true);

        assertThat(path, is(notNullValue()));
        assertThat(path.getForward(), is(notNullValue()));
        assertThat(path.getForward().getSegments().size(), is(3));
        assertThat(path.getReverse(), is(notNullValue()));
        assertThat(path.getReverse().getSegments().size(), is(3));
    }

    private static List<IslImmutableView> getBidirectionalIsls(Switch srcSwitch, int srcPort,
                                                               Switch dstSwitch, int dstPort) {
        return Lists.newArrayList(
                getIslView(srcSwitch, srcPort, dstSwitch, dstPort),
                getIslView(dstSwitch, dstPort, srcSwitch, srcPort));
    }

    private static IslImmutableView getIslView(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort) {
        IslImmutableView isl = mock(IslImmutableView.class);
        when(isl.getSrcSwitchId()).thenReturn(srcSwitch.getSwitchId());
        when(isl.getSrcPort()).thenReturn(srcPort);
        when(isl.getDestSwitchId()).thenReturn(dstSwitch.getSwitchId());
        when(isl.getDestPort()).thenReturn(dstPort);
        when(isl.getCost()).thenReturn(10);
        when(isl.getLatency()).thenReturn(33L);
        when(isl.getAvailableBandwidth()).thenReturn(1000L);
        when(isl.isUnderMaintenance()).thenReturn(false);
        when(isl.isUnstable()).thenReturn(false);
        return isl;
    }

    @Test
    public void shouldNotFindProtectedPathWithSharedSwitch() throws RecoverableException, UnroutableFlowException {
        // Topology:
        //  Already created flow: A-B-C-E-F
        //  No protected path here for this flow
        //       G----F
        //       |    |
        //  B----C----E
        //  |    |
        //  A----D

        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchA, 3, switchD, 4));
        isls.addAll(getBidirectionalIsls(switchB, 5, switchC, 6));
        isls.addAll(getBidirectionalIsls(switchD, 7, switchC, 8));
        isls.addAll(getBidirectionalIsls(switchC, 1, switchG, 2));
        isls.addAll(getBidirectionalIsls(switchC, 2, switchE, 3));
        isls.addAll(getBidirectionalIsls(switchG, 3, switchF, 4));
        isls.addAll(getBidirectionalIsls(switchE, 5, switchF, 6));

        List<PathSegment> forwardSegments = new ArrayList<>();
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchA).srcPort(10)
                .destSwitch(switchB).destPort(10).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchB).srcPort(11)
                .destSwitch(switchC).destPort(11).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchC).srcPort(12)
                .destSwitch(switchE).destPort(12).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchE).srcPort(13)
                .destSwitch(switchF).destPort(13).build());

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchF)
                .pathId(FORWARD_PATH_ID)
                .segments(forwardSegments)
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(java.util.Optional.of(forwardPath));


        List<PathSegment> reverseSegments = new ArrayList<>();
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchF).srcPort(13)
                .destSwitch(switchE).destPort(13).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchE).srcPort(12)
                .destSwitch(switchC).destPort(12).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchC).srcPort(11)
                .destSwitch(switchB).destPort(11).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchB).srcPort(10)
                .destSwitch(switchA).destPort(10).build());

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchF)
                .destSwitch(switchA)
                .pathId(REVERSE_PATH_ID)
                .segments(reverseSegments)
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow(switchA, switchF);
        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);

        when(config.getNetworkStrategy()).thenReturn(BuildStrategy.COST.name());
        when(islRepository.findActiveByBandwidthAndEncapsulationType(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(isls);

        when(flowPathRepository.findPathIdsByFlowDiverseGroupId(DIVERSE_GROUP_ID))
                .thenReturn(Lists.newArrayList(FORWARD_PATH_ID, REVERSE_PATH_ID));

        // check diversity counter
        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());
        assertEquals(2, availableNetwork.getSwitch(switchC.getSwitchId()).getDiversityGroupUseCounter());

        // check found path
        PathComputer pathComputer = new InMemoryPathComputer(
                availableNetworkFactory, new BestWeightAndShortestPathFinder(200), config);

        assertThrows(UnroutableFlowException.class, () -> pathComputer.getPath(flow, Collections.emptyList(), true));
    }

    private static Flow getFlow(Switch from, Switch to) {
        Flow flow = Flow.builder()
                .flowId("test-id")
                .srcSwitch(from)
                .destSwitch(to)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .ignoreBandwidth(false)
                .diverseGroupId(DIVERSE_GROUP_ID)
                .maxLatency(Long.MAX_VALUE)
                .pathComputationStrategy(PATH_COMPUTATION_STRATEGY)
                .allocateProtectedPath(true)
                .build();
        flow.setForwardPathId(new PathId("forward_path_id"));
        flow.setReversePathId(new PathId("reverse_path_id"));
        return flow;
    }
}
