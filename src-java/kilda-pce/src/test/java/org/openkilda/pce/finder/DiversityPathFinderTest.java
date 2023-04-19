/* Copyright 2023 Telstra Open Source
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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
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
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.impl.InMemoryPathComputer;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.IslRepository.IslImmutableView;
import org.openkilda.persistence.repositories.RepositoryFactory;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RunWith(Parameterized.class)
public class DiversityPathFinderTest {
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    public static final SwitchId SWITCH_ID_5 = new SwitchId(5);
    public static final Switch switchA = Switch.builder().switchId(SWITCH_ID_1).build();
    public static final Switch switchB = Switch.builder().switchId(SWITCH_ID_2).build();
    public static final Switch switchC = Switch.builder().switchId(SWITCH_ID_3).build();
    public static final Switch switchD = Switch.builder().switchId(SWITCH_ID_4).build();
    public static final Switch switchE = Switch.builder().switchId(SWITCH_ID_5).build();
    public static final PathId FORWARD_PATH_ID = new PathId("path_id_1");
    public static final PathId REVERSE_PATH_ID = new PathId("path_id_2");
    public static final String DIVERSE_GROUP_ID = "group_id_1";

    @Mock
    private PathComputerConfig config;
    @Mock
    private RepositoryFactory repositoryFactory;
    @Mock
    private IslRepository islRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private HaFlowPathRepository haFlowPathRepository;

    private AvailableNetworkFactory availableNetworkFactory;

    @Parameter
    public PathComputationStrategy pathComputationStrategy;

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
        when(repositoryFactory.createHaFlowPathRepository()).thenReturn(haFlowPathRepository);

        availableNetworkFactory = new AvailableNetworkFactory(config, repositoryFactory);
    }

    @Test
    public void chooseSamePathTest() throws RecoverableException, UnroutableFlowException {
        // Topology:
        // A----B----C     Already created flow: A-B-C
        //                 Requested flow: A-B-C

        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchB, 3, switchC, 4));

        List<PathSegment> forwardSegments = new ArrayList<>();
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchA).srcPort(10)
                .destSwitch(switchB).destPort(10).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchB).srcPort(11)
                .destSwitch(switchC).destPort(11).build());

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .pathId(FORWARD_PATH_ID)
                .segments(forwardSegments)
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(java.util.Optional.of(forwardPath));


        List<PathSegment> reverseSegments = new ArrayList<>();
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchC).srcPort(11)
                .destSwitch(switchB).destPort(11).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchB).srcPort(10)
                .destSwitch(switchA).destPort(10).build());

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .pathId(REVERSE_PATH_ID)
                .segments(reverseSegments)
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow();
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchC);
        flow.setDiverseGroupId(DIVERSE_GROUP_ID);
        flow.setForwardPathId(new PathId("forward_path_id"));
        flow.setReversePathId(new PathId("reverse_path_id"));
        flow.setMaxLatency(Long.MAX_VALUE);
        flow.setPathComputationStrategy(pathComputationStrategy);

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

        GetPathsResult path = pathComputer.getPath(flow);
        assertThat(path, is(notNullValue()));

        assertThat(path.getForward(), is(notNullValue()));
        Segment firstSegment = path.getForward().getSegments().get(0);
        assertAll(
                () -> assertThat(firstSegment.getSrcSwitchId(), equalTo(switchA.getSwitchId())),
                () -> assertThat(firstSegment.getDestSwitchId(), equalTo(switchB.getSwitchId()))
        );
        Segment lastSegment = path.getForward().getSegments().get(1);
        assertAll(
                () -> assertThat(lastSegment.getSrcSwitchId(), equalTo(switchB.getSwitchId())),
                () -> assertThat(lastSegment.getDestSwitchId(), equalTo(switchC.getSwitchId()))
        );

        assertThat(path.getReverse(), is(notNullValue()));
        Segment firstReverseSegment = path.getReverse().getSegments().get(0);
        assertAll(
                () -> assertThat(firstReverseSegment.getSrcSwitchId(), equalTo(switchC.getSwitchId())),
                () -> assertThat(firstReverseSegment.getDestSwitchId(), equalTo(switchB.getSwitchId()))
        );
        Segment lastReverseSegment = path.getReverse().getSegments().get(1);
        assertAll(
                () -> assertThat(lastReverseSegment.getSrcSwitchId(), equalTo(switchB.getSwitchId())),
                () -> assertThat(lastReverseSegment.getDestSwitchId(), equalTo(switchA.getSwitchId()))
        );
    }

    @Test
    public void useSameSwitchTest() throws RecoverableException, UnroutableFlowException {
        // Topology:
        //      D
        //      |
        // A----B----C     Already created flow: A-B-C
        //      |          Requested flow: D-B-E
        //      E

        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchB, 3, switchC, 4));
        isls.addAll(getBidirectionalIsls(switchD, 5, switchB, 6));
        isls.addAll(getBidirectionalIsls(switchB, 7, switchE, 8));

        List<PathSegment> forwardSegments = new ArrayList<>();
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchA).srcPort(10)
                .destSwitch(switchB).destPort(10).build());
        forwardSegments.add(PathSegment.builder().pathId(FORWARD_PATH_ID).srcSwitch(switchB).srcPort(11)
                .destSwitch(switchC).destPort(11).build());

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .pathId(FORWARD_PATH_ID)
                .segments(forwardSegments)
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(java.util.Optional.of(forwardPath));


        List<PathSegment> reverseSegments = new ArrayList<>();
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchC).srcPort(11)
                .destSwitch(switchB).destPort(11).build());
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchB).srcPort(10)
                .destSwitch(switchA).destPort(10).build());

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .pathId(REVERSE_PATH_ID)
                .segments(reverseSegments)
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow();
        flow.setSrcSwitch(switchD);
        flow.setDestSwitch(switchE);
        flow.setDiverseGroupId(DIVERSE_GROUP_ID);
        flow.setForwardPathId(new PathId("forward_path_id"));
        flow.setReversePathId(new PathId("reverse_path_id"));
        flow.setMaxLatency(Long.MAX_VALUE);
        flow.setPathComputationStrategy(pathComputationStrategy);

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

        GetPathsResult path = pathComputer.getPath(flow);
        assertThat(path, is(notNullValue()));

        assertThat(path.getForward(), is(notNullValue()));
        Segment firstSegment = path.getForward().getSegments().get(0);
        assertAll(
                () -> assertThat(firstSegment.getSrcSwitchId(), equalTo(switchD.getSwitchId())),
                () -> assertThat(firstSegment.getDestSwitchId(), equalTo(switchB.getSwitchId()))
        );
        Segment lastSegment = path.getForward().getSegments().get(1);
        assertAll(
                () -> assertThat(lastSegment.getSrcSwitchId(), equalTo(switchB.getSwitchId())),
                () -> assertThat(lastSegment.getDestSwitchId(), equalTo(switchE.getSwitchId()))
        );

        assertThat(path.getReverse(), is(notNullValue()));
        Segment firstReverseSegment = path.getReverse().getSegments().get(0);
        assertAll(
                () -> assertThat(firstReverseSegment.getSrcSwitchId(), equalTo(switchE.getSwitchId())),
                () -> assertThat(firstReverseSegment.getDestSwitchId(), equalTo(switchB.getSwitchId()))
        );
        Segment lastReverseSegment = path.getReverse().getSegments().get(1);
        assertAll(
                () -> assertThat(lastReverseSegment.getSrcSwitchId(), equalTo(switchB.getSwitchId())),
                () -> assertThat(lastReverseSegment.getDestSwitchId(), equalTo(switchD.getSwitchId()))
        );

    }

    @Test
    public void chooseLongerPathTest() throws RecoverableException, UnroutableFlowException {
        // Topology:
        //      D----E
        //      |    |
        // A----B----C     Already created flow: A-B-C
        //                 Requested flow: A-B-D-E-C

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
        reverseSegments.add(PathSegment.builder().pathId(REVERSE_PATH_ID).srcSwitch(switchB).srcPort(2)
                .destSwitch(switchA).destPort(1).build());

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .pathId(REVERSE_PATH_ID)
                .segments(reverseSegments)
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(java.util.Optional.of(reversePath));

        Flow flow = getFlow();
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchC);
        flow.setDiverseGroupId(DIVERSE_GROUP_ID);
        flow.setForwardPathId(new PathId("forward_path_id"));
        flow.setReversePathId(new PathId("reverse_path_id"));
        flow.setMaxLatency(Long.MAX_VALUE);
        flow.setPathComputationStrategy(pathComputationStrategy);

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

        GetPathsResult path = pathComputer.getPath(flow);
        assertThat(path, is(notNullValue()));
        assertThat(path.getForward(), is(notNullValue()));
        assertThat(path.getForward().getSegments().size(), is(4));
        assertThat(path.getReverse(), is(notNullValue()));
        assertThat(path.getReverse().getSegments().size(), is(4));

        Segment firstSegment = path.getForward().getSegments().get(0);
        assertAll(
                () -> assertThat(firstSegment.getSrcSwitchId(), equalTo(switchA.getSwitchId())),
                () -> assertThat(firstSegment.getDestSwitchId(), equalTo(switchB.getSwitchId()))
        );
        Segment lastSegment = path.getForward().getSegments().get(3);
        assertAll(
                () -> assertThat(lastSegment.getSrcSwitchId(), equalTo(switchE.getSwitchId())),
                () -> assertThat(lastSegment.getDestSwitchId(), equalTo(switchC.getSwitchId()))
        );

        Segment firstReverseSegment = path.getReverse().getSegments().get(0);
        assertAll(
                () -> assertThat(firstReverseSegment.getSrcSwitchId(), equalTo(switchC.getSwitchId())),
                () -> assertThat(firstReverseSegment.getDestSwitchId(), equalTo(switchE.getSwitchId()))
        );
        Segment lastReverseSegment = path.getReverse().getSegments().get(3);
        assertAll(
                () -> assertThat(lastReverseSegment.getSrcSwitchId(), equalTo(switchB.getSwitchId())),
                () -> assertThat(lastReverseSegment.getDestSwitchId(), equalTo(switchA.getSwitchId()))
        );
    }

    @Test
    public void choosePathWithoutSingleSwitchFlowTest() throws RecoverableException, UnroutableFlowException {
        // Topology:
        // -----E----D
        // |         |
        // A----B----C     Already created single switch flow: E
        //                 Requested flow: A-B-C-D

        List<IslImmutableView> isls = new ArrayList<>();
        isls.addAll(getBidirectionalIsls(switchA, 1, switchB, 2));
        isls.addAll(getBidirectionalIsls(switchB, 3, switchC, 4));
        isls.addAll(getBidirectionalIsls(switchC, 5, switchD, 6));
        isls.addAll(getBidirectionalIsls(switchD, 7, switchE, 8));
        isls.addAll(getBidirectionalIsls(switchE, 9, switchA, 10));

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchE)
                .destSwitch(switchE)
                .pathId(FORWARD_PATH_ID)
                .segments(Collections.emptyList())
                .build();
        when(flowPathRepository.findById(FORWARD_PATH_ID)).thenReturn(Optional.of(forwardPath));

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchE)
                .destSwitch(switchE)
                .pathId(REVERSE_PATH_ID)
                .segments(Collections.emptyList())
                .build();
        when(flowPathRepository.findById(REVERSE_PATH_ID)).thenReturn(Optional.of(reversePath));

        Flow flow = getFlow();
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchD);
        flow.setDiverseGroupId(DIVERSE_GROUP_ID);
        flow.setForwardPathId(new PathId("forward_path_id"));
        flow.setReversePathId(new PathId("reverse_path_id"));
        flow.setMaxLatency(Long.MAX_VALUE);
        flow.setPathComputationStrategy(pathComputationStrategy);

        when(config.getNetworkStrategy()).thenReturn(BuildStrategy.COST.name());
        when(islRepository.findActiveByBandwidthAndEncapsulationType(flow.getBandwidth(), flow.getEncapsulationType()))
                .thenReturn(isls);

        when(flowPathRepository.findPathIdsByFlowDiverseGroupId(DIVERSE_GROUP_ID))
                .thenReturn(Lists.newArrayList(FORWARD_PATH_ID, REVERSE_PATH_ID));

        // check diversity counter
        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());
        assertEquals(2, availableNetwork.getSwitch(switchE.getSwitchId()).getDiversityGroupUseCounter());

        // check found path
        PathComputer pathComputer = new InMemoryPathComputer(
                availableNetworkFactory, new BestWeightAndShortestPathFinder(200), config);

        GetPathsResult path = pathComputer.getPath(flow);
        assertThat(path, is(notNullValue()));
        assertThat(path.getForward(), is(notNullValue()));
        assertThat(path.getForward().getSegments().size(), is(3));
        assertThat(path.getReverse(), is(notNullValue()));
        assertThat(path.getReverse().getSegments().size(), is(3));

        Segment firstSegment = path.getForward().getSegments().get(0);
        assertAll(
                () -> assertThat(firstSegment.getSrcSwitchId(), equalTo(switchA.getSwitchId())),
                () -> assertThat(firstSegment.getDestSwitchId(), equalTo(switchB.getSwitchId()))
        );
        Segment lastSegment = path.getForward().getSegments().get(2);
        assertAll(
                () -> assertThat(lastSegment.getSrcSwitchId(), equalTo(switchC.getSwitchId())),
                () -> assertThat(lastSegment.getDestSwitchId(), equalTo(switchD.getSwitchId()))
        );

        Segment firstReverseSegment = path.getReverse().getSegments().get(0);
        assertAll(
                () -> assertThat(firstReverseSegment.getSrcSwitchId(), equalTo(switchD.getSwitchId())),
                () -> assertThat(firstReverseSegment.getDestSwitchId(), equalTo(switchC.getSwitchId()))
        );
        Segment lastReverseSegment = path.getReverse().getSegments().get(2);
        assertAll(
                () -> assertThat(lastReverseSegment.getSrcSwitchId(), equalTo(switchB.getSwitchId())),
                () -> assertThat(lastReverseSegment.getDestSwitchId(), equalTo(switchA.getSwitchId()))
        );
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

    private static Flow getFlow() {
        return Flow.builder()
                .flowId("test-id")
                .srcSwitch(Switch.builder().switchId(new SwitchId("1")).build())
                .destSwitch(Switch.builder().switchId(new SwitchId("2")).build())
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(100)
                .ignoreBandwidth(false)
                .build();
    }

    /**
     * PathComputationStrategies.
     */
    @Parameters(name = "PathComputationStrategy = {0}")
    public static Object[][] data() {
        return new Object[][] {
                {PathComputationStrategy.MAX_LATENCY},
                {PathComputationStrategy.LATENCY},
                {PathComputationStrategy.COST},
                {PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH}
        };
    }
}
