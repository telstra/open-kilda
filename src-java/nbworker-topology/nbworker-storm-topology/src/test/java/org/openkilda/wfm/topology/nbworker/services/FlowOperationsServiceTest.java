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

package org.openkilda.wfm.topology.nbworker.services;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.messaging.command.BaseRerouteRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.FlowPatch;
import org.openkilda.messaging.model.PatchEndpoint;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.share.history.model.DumpType;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.nbworker.bolts.FlowOperationsCarrier;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService.UpdateFlowResult;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FlowOperationsServiceTest extends InMemoryGraphBasedTest {
    private static final String FLOW_ID_1 = "flow_1";
    private static final String FLOW_ID_2 = "flow_2";
    private static final String FLOW_ID_3 = "flow_3";
    private static final String Y_FLOW_ID_1 = "y_flow_1";
    private static final PathId FORWARD_PATH_1 = new PathId("forward_path_1");
    private static final PathId FORWARD_PATH_2 = new PathId("forward_path_2");
    private static final PathId FORWARD_PATH_3 = new PathId("forward_path_3");
    private static final PathId REVERSE_PATH_1 = new PathId("reverse_path_1");
    private static final PathId REVERSE_PATH_2 = new PathId("reverse_path_2");
    private static final PathId REVERSE_PATH_3 = new PathId("reverse_path_3");
    private static final long UNMASKED_COOKIE = 123;
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    private static final int VLAN_1 = 1;
    private static final int PORT_1 = 2;
    private static final int PORT_2 = 3;
    private static final int VLAN_2 = 4;
    private static final int VLAN_3 = 5;
    private static final String CORRELATION_ID = "some ID";

    private static FlowOperationsService flowOperationsService;
    private static FlowRepository flowRepository;
    private static YFlowRepository yFlowRepository;
    private static FlowPathRepository flowPathRepository;
    private static PathSegmentRepository pathSegmentRepository;
    private static SwitchRepository switchRepository;

    private Switch switchA;
    private Switch switchB;
    private Switch switchC;
    private Switch switchD;

    @BeforeAll
    public static void setUpOnce() {
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        pathSegmentRepository = persistenceManager.getRepositoryFactory().createPathSegmentRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        flowOperationsService = new FlowOperationsService(persistenceManager.getRepositoryFactory(),
                persistenceManager.getTransactionManager());
    }

    @BeforeEach
    public void init() {
        switchA = createSwitch(SWITCH_ID_1);
        switchB = createSwitch(SWITCH_ID_2);
        switchC = createSwitch(SWITCH_ID_3);
        switchD = createSwitch(SWITCH_ID_4);
    }

    @Test
    public void updateMaxLatencyPriorityAndPinnedFlowFieldsTest() throws FlowNotFoundException, InvalidFlowException {
        String testFlowId = "flow_id";
        Long maxLatency = 555L;
        Integer priority = 777;
        PathComputationStrategy pathComputationStrategy = PathComputationStrategy.LATENCY;
        String description = "new_description";

        Flow flow = new TestFlowBuilder()
                .flowId(testFlowId)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.COST)
                .description("description")
                .status(FlowStatus.UP)
                .build();
        flowRepository.add(flow);

        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(testFlowId)
                .maxLatency(maxLatency)
                .priority(priority)
                .pinned(true)
                .targetPathComputationStrategy(pathComputationStrategy)
                .description("new_description")
                .build();

        Flow updatedFlow = flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);

        assertEquals(maxLatency, updatedFlow.getMaxLatency());
        assertEquals(priority, updatedFlow.getPriority());
        assertEquals(pathComputationStrategy, updatedFlow.getTargetPathComputationStrategy());
        assertEquals(description, updatedFlow.getDescription());
        assertTrue(updatedFlow.isPinned());

        receivedFlow = FlowPatch.builder()
                .flowId(testFlowId)
                .build();
        updatedFlow = flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);

        assertEquals(maxLatency, updatedFlow.getMaxLatency());
        assertEquals(priority, updatedFlow.getPriority());
        assertEquals(pathComputationStrategy, updatedFlow.getTargetPathComputationStrategy());
        assertEquals(description, updatedFlow.getDescription());
        assertTrue(updatedFlow.isPinned());
    }

    @Test
    public void strictBandwidthAndIgnoreBandwidthPatchConflictTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            String testFlowId = "flow_id";

            Flow flow = new TestFlowBuilder()
                    .flowId(testFlowId)
                    .srcSwitch(switchA)
                    .destSwitch(switchB)
                    .build();
            flowRepository.add(flow);

            FlowPatch receivedFlow = FlowPatch.builder()
                    .flowId(testFlowId)
                    .strictBandwidth(true)
                    .ignoreBandwidth(true)
                    .build();

            flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        });

    }

    @Test
    public void strictBandwidthAndIgnoreBandwidthFlowConflictTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            String testFlowId = "flow_id";

            Flow flow = new TestFlowBuilder()
                    .flowId(testFlowId)
                    .srcSwitch(switchA)
                    .destSwitch(switchB)
                    .ignoreBandwidth(true)
                    .build();
            flowRepository.add(flow);

            FlowPatch receivedFlow = FlowPatch.builder()
                    .flowId(testFlowId)
                    .strictBandwidth(true)
                    .ignoreBandwidth(false)
                    .build();

            flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        });
    }

    @Test
    public void strictBandwidthUpdateTest() throws FlowNotFoundException, InvalidFlowException {
        String testFlowId = "flow_id";

        Flow flow = new TestFlowBuilder()
                .flowId(testFlowId)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();
        flowRepository.add(flow);

        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(testFlowId)
                .strictBandwidth(true)
                .build();

        Flow updatedFlow = flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);

        assertTrue(updatedFlow.isStrictBandwidth());
    }

    @Test
    public void updateFlowWouldNotChangeTheVlanStatsInTheFlow() throws FlowNotFoundException, InvalidFlowException {
        String testFlowId = "flow_id";
        Set<Integer> originalVlanStatistics = new HashSet<>();
        originalVlanStatistics.add(11);

        Flow flow = new TestFlowBuilder()
                .flowId(testFlowId)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .vlanStatistics(originalVlanStatistics)
                .build();
        flowRepository.add(flow);

        Set<Integer> expectedVlanStatistics = new HashSet<>();
        expectedVlanStatistics.add(31);

        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(testFlowId)
                .vlanStatistics(expectedVlanStatistics)
                .build();

        Flow updatedFlow = flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);

        assertThat(updatedFlow.getVlanStatistics(), containsInAnyOrder(originalVlanStatistics.toArray()));
    }

    @Test
    public void unableToUpdateVlanStatisticsOldVlansSetNewVlansNullTest() {
        assertThrows(IllegalArgumentException.class,
                () -> runUnableToUpdateVlanStatisticsTest(VLAN_1, VLAN_2, null, null));
    }

    @Test
    public void unableToUpdateVlanStatisticsOldVlansSetNewVlansSetTest() {
        assertThrows(IllegalArgumentException.class, () ->
                runUnableToUpdateVlanStatisticsTest(VLAN_1, VLAN_2, VLAN_2, VLAN_3)
        );
    }

    @Test
    public void unableToUpdateVlanStatisticsOldVlansZeroNewVlansSetTest() {
        assertThrows(IllegalArgumentException.class,
                () -> runUnableToUpdateVlanStatisticsTest(0, 0, VLAN_1, VLAN_2));
    }

    private void runUnableToUpdateVlanStatisticsTest(
            int oldSrcVlan, int oldDstVlan, Integer newSrcVlan, Integer newDstVLan)
            throws FlowNotFoundException, InvalidFlowException {
        Flow flow = new TestFlowBuilder()
                .flowId(FLOW_ID_1)
                .srcSwitch(switchA)
                .srcVlan(oldSrcVlan)
                .destSwitch(switchB)
                .destVlan(oldDstVlan)
                .vlanStatistics(new HashSet<>())
                .build();
        flowRepository.add(flow);

        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .vlanStatistics(Sets.newHashSet(1, 2, 3))
                .source(PatchEndpoint.builder().vlanId(newSrcVlan).build())
                .destination(PatchEndpoint.builder().vlanId(newDstVLan).build())
                .build();

        flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
    }

    @Test
    public void unableToSetProtectedPathForInitiallyOneSwitchFlowTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            createFlow(FLOW_ID_1, switchA, switchA, false);
            FlowPatch receivedFlow = FlowPatch.builder()
                    .flowId(FLOW_ID_1)
                    .allocateProtectedPath(true)
                    .build();
            flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        });
    }

    @Test
    public void unableToMakeOneSwitchFlowFromProtectedByUpdatingDstTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            createFlow(FLOW_ID_1, switchA, switchB, true);
            FlowPatch receivedFlow = FlowPatch.builder()
                    .flowId(FLOW_ID_1)
                    .destination(PatchEndpoint.builder().switchId(SWITCH_ID_1).build())
                    .build();
            flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        });
    }

    @Test
    public void unableToMakeOneSwitchFlowFromProtectedByUpdatingSrcTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            createFlow(FLOW_ID_1, switchA, switchB, true);
            FlowPatch receivedFlow = FlowPatch.builder()
                    .flowId(FLOW_ID_1)
                    .source(PatchEndpoint.builder().switchId(SWITCH_ID_2).build())
                    .build();
            flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        });
    }

    @Test
    public void unableToMakeProtectedOneSwitchFlowTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            createFlow(FLOW_ID_1, switchA, switchB, false);
            FlowPatch receivedFlow = FlowPatch.builder()
                    .flowId(FLOW_ID_1)
                    .source(PatchEndpoint.builder().switchId(SWITCH_ID_3).build())
                    .destination(PatchEndpoint.builder().switchId(SWITCH_ID_3).build())
                    .allocateProtectedPath(true)
                    .build();
            flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        });
    }

    @Test
    public void ableToMakeProtectedFlowFromMultiSwitchFlowTest() throws FlowNotFoundException, InvalidFlowException {
        createFlow(FLOW_ID_1, switchA, switchB, false);
        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .allocateProtectedPath(true)
                .build();
        flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        // no exception expected
    }

    @Test
    public void ableToMakeProtectedFlowFromOneSwitchByChangingSrcTest()
            throws FlowNotFoundException, InvalidFlowException {
        createFlow(FLOW_ID_1, switchA, switchA, false);
        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .source(PatchEndpoint.builder().switchId(SWITCH_ID_2).build())
                .allocateProtectedPath(true)
                .build();
        flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        // no exception expected
    }

    @Test
    public void ableToMakeProtectedFlowFromOneSwitchByChangingDstTest()
            throws FlowNotFoundException, InvalidFlowException {
        createFlow(FLOW_ID_1, switchA, switchA, false);
        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .destination(PatchEndpoint.builder().switchId(SWITCH_ID_2).build())
                .allocateProtectedPath(true)
                .build();
        flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        // no exception expected
    }

    @Test
    public void ableToMakeProtectedFlowFromOneSwitchByChangingSrcAndDstTest()
            throws FlowNotFoundException, InvalidFlowException {
        createFlow(FLOW_ID_1, switchA, switchA, false);
        FlowPatch receivedFlow = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .source(PatchEndpoint.builder().switchId(SWITCH_ID_2).build())
                .destination(PatchEndpoint.builder().switchId(SWITCH_ID_3).build())
                .allocateProtectedPath(true)
                .build();
        flowOperationsService.updateFlow(new FlowCarrierImpl(), receivedFlow, CORRELATION_ID);
        // no exception expected
    }

    @Test
    public void prepareFlowUpdateResultWithChangedStrategyTest() {
        // given: FlowPatch with COST strategy and Flow with MAX_LATENCY strategy
        String flowId = "test_flow_id";
        FlowPatch flowDto = FlowPatch.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.COST)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needUpdateFlow flag set to true
        assertTrue(result.isNeedUpdateFlow());
    }

    @Test
    public void prepareFlowUpdateResultWithChangedMaxLatencyFirstCaseTest() {
        // given: FlowPatch with max latency and no strategy and Flow with MAX_LATENCY strategy and no max latency
        String flowId = "test_flow_id";
        FlowPatch flowDto = FlowPatch.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needRerouteFlow flag set to true
        assertTrue(result.isNeedUpdateFlow());
    }

    @Test
    public void prepareFlowUpdateResultWithChangedMaxLatencySecondCaseTest() {
        // given: FlowPatch with max latency and MAX_LATENCY strategy
        //        and Flow with MAX_LATENCY strategy and no max latency
        String flowId = "test_flow_id";
        FlowPatch flowDto = FlowPatch.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needRerouteFlow flag set to true
        assertTrue(result.isNeedUpdateFlow());
    }

    @Test
    public void prepareFlowUpdateResultNotUpdateFirstCaseTest() {
        // given: FlowPatch with max latency and MAX_LATENCY strategy
        //        and Flow with MAX_LATENCY strategy and same max latency
        String flowId = "test_flow_id";
        FlowPatch flowDto = FlowPatch.builder()
                .flowId(flowId)
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        // when: compare this flows
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        // then: needRerouteFlow flag set to false
        Assertions.assertFalse(result.isNeedUpdateFlow());
    }

    @Test
    public void prepareFlowUpdateResultNotUpdateSecondCaseTest() {
        // given: FlowPatch with no max latency and no strategy
        //        and Flow with MAX_LATENCY strategy and max latency
        String flowId = "test_flow_id";
        FlowPatch flowDto = FlowPatch.builder()
                .flowId(flowId)
                .build();
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .maxLatency(100L)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();

        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowDto, flow).build();

        Assertions.assertFalse(result.isNeedUpdateFlow());
    }

    @Test
    public void prepareFlowUpdateResultWithNeedUpdateFlagTest() {
        String flowId = "test_flow_id";
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .srcPort(2)
                .srcVlan(3)
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .destPort(4)
                .destVlan(5)
                .bandwidth(1000)
                .allocateProtectedPath(true)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.COST)
                .maxLatency(1L)
                .maxLatencyTier2(1L)
                .build();

        // new src switch
        FlowPatch flowPatch = FlowPatch.builder()
                .source(PatchEndpoint.builder().switchId(new SwitchId(3)).build())
                .build();
        UpdateFlowResult result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        //new src port
        flowPatch = FlowPatch.builder().source(PatchEndpoint.builder().portNumber(9).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new src vlan
        flowPatch = FlowPatch.builder().source(PatchEndpoint.builder().vlanId(9).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new src inner vlan
        flowPatch = FlowPatch.builder().source(PatchEndpoint.builder().innerVlanId(9).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new src LLDP flag
        flowPatch = FlowPatch.builder().source(PatchEndpoint.builder().trackLldpConnectedDevices(true).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new src ARP flag
        flowPatch = FlowPatch.builder().source(PatchEndpoint.builder().trackArpConnectedDevices(true).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new dst switch
        flowPatch = FlowPatch.builder().destination(PatchEndpoint.builder().switchId(new SwitchId(3)).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        //new dst port
        flowPatch = FlowPatch.builder().destination(PatchEndpoint.builder().portNumber(9).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new dst vlan
        flowPatch = FlowPatch.builder().destination(PatchEndpoint.builder().vlanId(9).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new dst inner vlan
        flowPatch = FlowPatch.builder().destination(PatchEndpoint.builder().innerVlanId(9).build()).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new dst LLDP flag
        flowPatch = FlowPatch.builder()
                .destination(PatchEndpoint.builder().trackLldpConnectedDevices(true).build())
                .build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new dst ARP flag
        flowPatch = FlowPatch.builder()
                .destination(PatchEndpoint.builder().trackArpConnectedDevices(true).build())
                .build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new maximum bandwidth
        flowPatch = FlowPatch.builder().bandwidth(9000L).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new flag allocate protected path
        flowPatch = FlowPatch.builder().allocateProtectedPath(false).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // add diverse flow id
        flowPatch = FlowPatch.builder().diverseFlowId("diverse_flow_id").build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new ignore bandwidth flag
        flowPatch = FlowPatch.builder().ignoreBandwidth(true).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new encapsulation type
        flowPatch = FlowPatch.builder().encapsulationType(FlowEncapsulationType.VXLAN).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());

        // new path computation strategy
        flowPatch = FlowPatch.builder().pathComputationStrategy(PathComputationStrategy.LATENCY).build();
        result = flowOperationsService.prepareFlowUpdateResult(flowPatch, flow).build();
        assertTrue(result.isNeedUpdateFlow());
    }

    @Test
    public void getFlowsForEndpointNotReturnFlowsForOrphanedPaths() throws SwitchNotFoundException {
        Flow flow = createFlow(FLOW_ID_1, switchA, 1, switchC, 2,
                FORWARD_PATH_1, REVERSE_PATH_1, switchB);
        createOrphanFlowPaths(flow, switchA, 1, switchC, 2, FORWARD_PATH_3, REVERSE_PATH_3, switchD);
        assertEquals(0, flowOperationsService.getFlowsForEndpoint(switchD.getSwitchId(), null).size());
    }

    @Test
    public void getFlowsForEndpointOneSwitchFlowNoPortTest() throws SwitchNotFoundException {
        createFlow(FLOW_ID_1, switchA, 1, switchA, 2, FORWARD_PATH_1, REVERSE_PATH_1, null);

        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, null), FLOW_ID_1);

        createFlow(FLOW_ID_2, switchA, 3, switchA, 4, FORWARD_PATH_2, REVERSE_PATH_2, null);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, null), FLOW_ID_1, FLOW_ID_2);
    }

    @Test
    public void getFlowsForEndpointMultiSwitchFlowNoPortTest() throws SwitchNotFoundException {
        createFlow(FLOW_ID_1, switchA, 1, switchB, 2, FORWARD_PATH_1, REVERSE_PATH_1, null);

        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, null), FLOW_ID_1);

        createFlow(FLOW_ID_2, switchB, 3, switchA, 4, FORWARD_PATH_2, REVERSE_PATH_2, null);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, null), FLOW_ID_1, FLOW_ID_2);
    }

    @Test
    public void getFlowsForEndpointTransitSwitchFlowNoPortTest() throws SwitchNotFoundException {
        createFlow(FLOW_ID_1, switchA, 1, switchC, 2, FORWARD_PATH_1, REVERSE_PATH_1, switchB);

        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_2, null), FLOW_ID_1);

        createFlow(FLOW_ID_2, switchC, 3, switchA, 4, FORWARD_PATH_2, REVERSE_PATH_2, switchB);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_2, null), FLOW_ID_1, FLOW_ID_2);
    }

    @Test
    public void getFlowsForEndpointSeveralFlowNoPortTest() throws SwitchNotFoundException {
        // one switch flow
        createFlow(FLOW_ID_1, switchB, 1, switchB, 2, FORWARD_PATH_1, REVERSE_PATH_1, null);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_2, null), FLOW_ID_1);

        // two switches flow
        createFlow(FLOW_ID_2, switchA, 3, switchB, 4, FORWARD_PATH_2, REVERSE_PATH_2, null);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_2, null), FLOW_ID_1, FLOW_ID_2);

        // three switches flow
        createFlow(FLOW_ID_3, switchA, 5, switchC, 6, FORWARD_PATH_3, REVERSE_PATH_3, switchB);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_2, null), FLOW_ID_1, FLOW_ID_2, FLOW_ID_3);
    }

    @Test
    public void getFlowsForEndpointOneSwitchFlowWithPortTest() throws SwitchNotFoundException {
        createFlow(FLOW_ID_1, switchA, 1, switchA, 2, FORWARD_PATH_1, REVERSE_PATH_1, null);

        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, 1), FLOW_ID_1);

        // flow on different port
        createFlow(FLOW_ID_2, switchA, 3, switchA, 4, FORWARD_PATH_2, REVERSE_PATH_2, null);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, 1), FLOW_ID_1);
    }

    @Test
    public void getFlowsForEndpointMultiSwitchFlowWithPortTest() throws SwitchNotFoundException {
        createFlow(FLOW_ID_1, switchA, 1, switchB, 2, FORWARD_PATH_1, REVERSE_PATH_1, null);

        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, 1), FLOW_ID_1);

        createFlow(FLOW_ID_2, switchB, 3, switchA, 1, FORWARD_PATH_2, REVERSE_PATH_2, null);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_1, 1), FLOW_ID_1, FLOW_ID_2);
    }

    @Test
    public void getFlowsForEndpointTransitSwitchFlowWithPortTest() throws SwitchNotFoundException {
        createFlow(FLOW_ID_1, switchA, 1, switchC, 2, FORWARD_PATH_1, REVERSE_PATH_1, switchB);

        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_2, 2), FLOW_ID_1);

        createFlow(FLOW_ID_2, switchC, 2, switchA, 4, FORWARD_PATH_2, REVERSE_PATH_2, switchB);
        assertFlows(flowOperationsService.getFlowsForEndpoint(SWITCH_ID_2, 2), FLOW_ID_1, FLOW_ID_2);
    }

    @Test
    public void patchFlowWithLatencyOnlyTest() throws FlowNotFoundException, InvalidFlowException {
        Flow createdFlow = createFlow(FLOW_ID_1, switchA, 1, switchC, 2, FORWARD_PATH_1,
                REVERSE_PATH_1, switchB);
        Assertions.assertNotEquals(Long.valueOf(100_500L), createdFlow.getMaxLatency());
        FlowPatch flowPatch = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .maxLatency(100_500L)
                .build();

        Flow updatedFlow = flowOperationsService.updateFlow(new FlowCarrierImpl(), flowPatch, CORRELATION_ID);
        assertEquals(Long.valueOf(100_500L), updatedFlow.getMaxLatency());
    }

    @Test
    public void patchFlowWithLatencyAndLatencyTier2Test() throws FlowNotFoundException, InvalidFlowException {
        Flow createdFlow = createFlow(FLOW_ID_1, switchA, 1, switchC, 2,
                FORWARD_PATH_1, REVERSE_PATH_1, switchB);
        Assertions.assertNotEquals(Long.valueOf(100_500L), createdFlow.getMaxLatency());
        Assertions.assertNotEquals(Long.valueOf(420_000L), createdFlow.getMaxLatencyTier2());

        FlowPatch flowPatch = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .maxLatency(100_500L)
                .maxLatencyTier2(420_000L)
                .build();

        Flow updatedFlow = flowOperationsService.updateFlow(new FlowCarrierImpl(), flowPatch, CORRELATION_ID);

        assertEquals(Long.valueOf(100_500L), updatedFlow.getMaxLatency());
        assertEquals(Long.valueOf(420_000L), updatedFlow.getMaxLatencyTier2());
    }

    @Test
    public void whenFlowWithNullLatency_patchFlowWithLatencyTier2OnlyThrowsAnException() {
        Flow createdFlow = createFlow(FLOW_ID_1, switchA, 1, switchC, 2,
                FORWARD_PATH_1, REVERSE_PATH_1, switchB);
        assertNull(createdFlow.getMaxLatency());

        FlowPatch flowPatch = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .maxLatencyTier2(420_000L)
                .build();

        assertThrows(InvalidFlowException.class,
                () -> flowOperationsService.updateFlow(new FlowCarrierImpl(), flowPatch, CORRELATION_ID),
                "Max latency tier 2 cannot be used without max latency parameter");
    }

    @Test
    public void whenFlowWithMaxLatency_patchFlowWithLatencyTier2OnlyTest()
            throws FlowNotFoundException, InvalidFlowException {
        Flow createdFlow = createFlow(FLOW_ID_1, switchA, 1, switchC, 2,
                FORWARD_PATH_1, REVERSE_PATH_1, switchB, false,
                100_500L, 0L);
        FlowPatch flowPatch = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .maxLatencyTier2(420_000L)
                .build();

        Flow updatedFlow = flowOperationsService.updateFlow(new FlowCarrierImpl(), flowPatch, CORRELATION_ID);

        assertEquals(Long.valueOf(100_500L), updatedFlow.getMaxLatency());
        assertEquals(Long.valueOf(420_000L), updatedFlow.getMaxLatencyTier2());
    }

    @Test
    public void whenPartialUpdate_dumpBeforeAndDumpAfterIsSaved() throws FlowNotFoundException, InvalidFlowException {
        Flow createdFlow = createFlow(FLOW_ID_1, switchA, 1, switchC, 2,
                FORWARD_PATH_1, REVERSE_PATH_1, switchB, false,
                100_500L, 0L);
        FlowPatch flowPatch = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .priority(4)
                .build();
        FlowCarrierImpl carrier = new FlowCarrierImpl();

        assertTrue(carrier.getHistoryHolderList().isEmpty());
        Flow updatedFlow = flowOperationsService.updateFlow(carrier, flowPatch, CORRELATION_ID);

        assertEquals(3, carrier.getHistoryHolderList().size());

        String event = "Flow partial updating";
        assertEquals(event, carrier.getHistoryHolderList().get(0).getFlowHistoryData().getAction());

        assertEquals(DumpType.STATE_BEFORE, carrier.getHistoryHolderList().get(1).getFlowDumpData().getDumpType());
        assertEquals(DumpType.STATE_AFTER, carrier.getHistoryHolderList().get(2).getFlowDumpData().getDumpType());

        String action = "Flow PATCH operation has been executed without the consecutive update.";
        assertEquals(action, carrier.getHistoryHolderList().get(2).getFlowHistoryData().getAction());
    }

    @Test
    public void whenFullUpdateIsRequired_historyActionIsSaved() throws FlowNotFoundException, InvalidFlowException {
        Flow createdFlow = createFlow(FLOW_ID_1, switchA, 1, switchC, 2,
                FORWARD_PATH_1, REVERSE_PATH_1, switchB, false,
                100_500L, 0L);
        FlowPatch flowPatch = FlowPatch.builder()
                .flowId(FLOW_ID_1)
                .allocateProtectedPath(true)
                .build();
        FlowCarrierImpl carrier = new FlowCarrierImpl();

        assertTrue(carrier.getHistoryHolderList().isEmpty());
        Flow updatedFlow = flowOperationsService.updateFlow(carrier, flowPatch, CORRELATION_ID);

        assertEquals(3, carrier.getHistoryHolderList().size());

        String event = "Flow partial updating";
        assertEquals(event, carrier.getHistoryHolderList().get(0).getFlowHistoryData().getAction());

        assertEquals(DumpType.STATE_BEFORE, carrier.getHistoryHolderList().get(1).getFlowDumpData().getDumpType());

        String action = "Full update is required. Executing the UPDATE operation.";
        assertEquals(action, carrier.getHistoryHolderList().get(2).getFlowHistoryData().getAction());
    }

    @Test
    public void makeRerouteRequests() {
        YFlow yFlow = buildYFlow(Y_FLOW_ID_1, switchA, 1, switchD);
        yFlowRepository.add(yFlow);

        Flow ySubflow1 = buildFlow(null, Y_FLOW_ID_1, switchA, 1, 10,
                switchB, 2, 11, "subFlow1", yFlow);
        FlowPath yFlowForwardPath1 = FlowPath.builder()
                .pathId(new PathId("subPath1"))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flow(ySubflow1)
                .build();

        Flow ySubflow2 = buildFlow(null, Y_FLOW_ID_1, switchA, 1, 20,
                switchC, 2, 22, "subFlow2", yFlow);
        FlowPath yFlowForwardPath2 = FlowPath.builder()
                .pathId(new PathId("subPath2"))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flow(ySubflow2)
                .build();

        Flow flow1 = buildFlow(FLOW_ID_1, null, switchA, 1, 100, switchB,
                2, 111, "regularFlow", null);
        FlowPath flowPath1 = FlowPath.builder()
                .pathId(new PathId("path1"))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flow(flow1)
                .build();

        List<FlowPath> flowPaths = Arrays.asList(yFlowForwardPath1, flowPath1, yFlowForwardPath2);

        // 3 flow path: 1 for regular flow and 2 for y-flow
        List<BaseRerouteRequest> actualResult =
                flowOperationsService.makeRerouteRequests(flowPaths, new HashSet<>(), "Great reason to reroute");

        Assertions.assertEquals(2, actualResult.size());
        Assertions.assertInstanceOf(YFlowRerouteRequest.class, actualResult.get(0));
        Assertions.assertEquals(Y_FLOW_ID_1, actualResult.get(0).getFlowId());
        Assertions.assertInstanceOf(FlowRerouteRequest.class, actualResult.get(1));
        Assertions.assertEquals(FLOW_ID_1, actualResult.get(1).getFlowId());

        // y-flow does not exist in the repository
        transactionManager.doInTransaction(() -> yFlowRepository.remove(yFlow));
        actualResult = flowOperationsService.makeRerouteRequests(flowPaths, new HashSet<>(), "Great reason to reroute");
        Assertions.assertEquals(1, actualResult.size());
        Assertions.assertInstanceOf(FlowRerouteRequest.class, actualResult.get(0));
        Assertions.assertEquals(FLOW_ID_1, actualResult.get(0).getFlowId());
    }

    private YFlow buildYFlow(String yFlowId, Switch sharedEndpoint, int portNumber, Switch yPoint) {
        return YFlow.builder()
                .yFlowId(yFlowId)
                .sharedEndpoint(new SharedEndpoint(sharedEndpoint.getSwitchId(), portNumber))
                .yPoint(yPoint.getSwitchId())
                .status(FlowStatus.UP)
                .build();
    }

    private Flow buildFlow(String flowId, String yflowId, Switch srcSwitch, int srcPort, int srcVlan, Switch destSwitch,
                           int destPort, int destVlan, String desc, YFlow yflow) {
        TestFlowBuilder builder = new TestFlowBuilder()
                .yFlow(yflow)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .destVlan(destVlan)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.COST)
                .description(desc)
                .status(FlowStatus.UP);

        if (flowId != null) {
            builder.flowId(flowId);
        }
        if (yflowId != null) {
            builder.yFlowId(yflowId);
        }
        return builder.build();
    }

    private void assertFlows(Collection<Flow> actualFlows, String... expectedFlowIds) {
        assertEquals(expectedFlowIds.length, actualFlows.size());
        assertEquals(new HashSet<>(Arrays.asList(expectedFlowIds)),
                actualFlows.stream().map(Flow::getFlowId).collect(Collectors.toSet()));
    }

    private Switch createSwitch(SwitchId switchId) {
        Switch sw = Switch.builder().switchId(switchId).status(SwitchStatus.ACTIVE).build();
        switchRepository.add(sw);
        return sw;
    }

    private void createOrphanFlowPaths(Flow flow, Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort,
                                       PathId forwardPartId, PathId reversePathId, Switch transitSwitch) {
        FlowPath forwardPath = FlowPath.builder()
                .pathId(forwardPartId)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, UNMASKED_COOKIE))
                .build();

        FlowPath reversePath = FlowPath.builder()
                .pathId(reversePathId)
                .srcSwitch(dstSwitch)
                .destSwitch(srcSwitch)
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, UNMASKED_COOKIE))
                .build();

        if (!srcSwitch.getSwitchId().equals(dstSwitch.getSwitchId())) {
            if (transitSwitch == null) {
                // direct paths between src and dst switches
                forwardPath.setSegments(newArrayList(createPathSegment(forwardPath.getPathId(),
                        srcSwitch, srcPort, dstSwitch, dstPort)));
                reversePath.setSegments(newArrayList(createPathSegment(reversePath.getPathId(),
                        dstSwitch, dstPort, srcSwitch, srcPort)));
            } else {
                // src switch ==> transit switch ==> dst switch
                forwardPath.setSegments(newArrayList(
                        createPathSegment(forwardPath.getPathId(), srcSwitch, srcPort, transitSwitch, srcPort),
                        createPathSegment(forwardPath.getPathId(), transitSwitch, dstPort, dstSwitch, dstPort)));
                reversePath.setSegments(newArrayList(
                        createPathSegment(reversePath.getPathId(), dstSwitch, dstPort, transitSwitch, dstPort),
                        createPathSegment(reversePath.getPathId(), transitSwitch, srcPort, srcSwitch, srcPort)));

            }
        }

        flowPathRepository.add(forwardPath);
        flowPathRepository.add(reversePath);

        flow.addPaths(forwardPath, reversePath);
    }

    private void createFlow(String flowId, Switch srcSwitch, Switch dstSwitch, Boolean protectedPath) {
        createFlow(flowId, srcSwitch, PORT_1, dstSwitch, PORT_2,
                FORWARD_PATH_1, REVERSE_PATH_1, null, protectedPath, null, null);
    }

    private Flow createFlow(String flowId, Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort,
                            PathId forwardPartId, PathId reversePathId, Switch transitSwitch) {
        return createFlow(
                flowId, srcSwitch, srcPort, dstSwitch, dstPort,
                forwardPartId, reversePathId, transitSwitch, false,
                null, null);
    }

    private Flow createFlow(String flowId, Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort,
                            PathId forwardPartId, PathId reversePathId, Switch transitSwitch, boolean protectedPath,
                            Long maxLatency, Long maxLatencyTier2) {

        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .status(FlowStatus.UP)
                .allocateProtectedPath(protectedPath)
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .pathId(forwardPartId)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, UNMASKED_COOKIE))
                .build();

        FlowPath reversePath = FlowPath.builder()
                .pathId(reversePathId)
                .srcSwitch(dstSwitch)
                .destSwitch(srcSwitch)
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, UNMASKED_COOKIE))
                .build();

        if (!srcSwitch.getSwitchId().equals(dstSwitch.getSwitchId())) {
            if (transitSwitch == null) {
                // direct paths between src and dst switches
                forwardPath.setSegments(newArrayList(createPathSegment(forwardPath.getPathId(),
                        srcSwitch, srcPort, dstSwitch, dstPort)));
                reversePath.setSegments(newArrayList(createPathSegment(reversePath.getPathId(),
                        dstSwitch, dstPort, srcSwitch, srcPort)));
            } else {
                // src switch ==> transit switch ==> dst switch
                forwardPath.setSegments(newArrayList(
                        createPathSegment(forwardPath.getPathId(), srcSwitch, srcPort, transitSwitch, srcPort),
                        createPathSegment(forwardPath.getPathId(), transitSwitch, dstPort, dstSwitch, dstPort)));
                reversePath.setSegments(newArrayList(
                        createPathSegment(reversePath.getPathId(), dstSwitch, dstPort, transitSwitch, dstPort),
                        createPathSegment(reversePath.getPathId(), transitSwitch, srcPort, srcSwitch, srcPort)));

            }
        }

        flow.setForwardPath(forwardPath);
        flow.setReversePath(reversePath);
        flowRepository.add(flow);
        return flow;
    }

    private PathSegment createPathSegment(PathId pathId, Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort) {
        PathSegment pathSegment = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .build();
        pathSegmentRepository.add(pathSegment);
        return pathSegment;
    }

    private static class FlowCarrierImpl implements FlowOperationsCarrier {
        private List<FlowHistoryHolder> historyHolderList = new ArrayList<>();

        @Override
        public void emitPeriodicPingUpdate(String flowId, boolean enabled) {

        }

        @Override
        public void sendUpdateRequest(FlowRequest request) {

        }

        @Override
        public void sendNorthboundResponse(InfoData data) {

        }

        @Override
        public void sendHistoryUpdate(FlowHistoryHolder historyHolder) {
            historyHolderList.add(historyHolder);
        }

        public List<FlowHistoryHolder> getHistoryHolderList() {
            return historyHolderList;
        }
    }
}
