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

package org.openkilda.wfm.topology.reroute.service;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.reroute.FlowType;
import org.openkilda.messaging.info.reroute.SwitchStateChanged;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSubType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.EntityNotFoundException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionCallback;
import org.openkilda.persistence.tx.TransactionCallbackWithoutResult;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.topology.reroute.bolts.MessageSender;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class RerouteServiceTest {

    private static final SwitchId SWITCH_ID_A = new SwitchId(1L);
    private static final Switch SWITCH_A = Switch.builder().switchId(SWITCH_ID_A).build();
    private static final SwitchId SWITCH_ID_B = new SwitchId(2L);
    private static final Switch SWITCH_B = Switch.builder().switchId(SWITCH_ID_B).build();
    private static final SwitchId SWITCH_ID_C = new SwitchId(3L);
    private static final Switch SWITCH_C = Switch.builder().switchId(SWITCH_ID_C).build();
    private static final SwitchId SWITCH_ID_D = new SwitchId(4L);
    private static final Switch SWITCH_D = Switch.builder().switchId(SWITCH_ID_D).build();
    private static final int PORT = 1;

    private static final PathNode PATH_NODE = new PathNode(SWITCH_ID_A, PORT, 1);
    private static final String REASON = "REASON";

    private static final RerouteInactiveFlows REROUTE_INACTIVE_FLOWS_COMMAND = new RerouteInactiveFlows(PATH_NODE,
            REASON);

    private static final String FLOW_ID = "TEST_FLOW";
    private static final String CORRELATION_ID = "CORRELATION_ID";
    private static final String YFLOW_ID = "TEST_YFLOW";
    private static final String SUB_YFLOW_ID = "TEST_SUB_YFLOW";
    private static final String HA_FLOW_ID = "TEST_HA_FLOW";
    private static final String HA_SUB_FLOW_ID_1 = "TEST_HA_SUB_FLOW_1";
    private static final String HA_SUB_FLOW_ID_2 = "TEST_HA_SUB_FLOW_2";
    private static final PathId PATH_ID_1 = new PathId("1");
    private static final PathId PATH_ID_2 = new PathId("2");
    private static final PathId PATH_ID_3 = new PathId("3");
    private static final PathId PATH_ID_4 = new PathId("4");
    private static final PathId PATH_ID_5 = new PathId("5");
    private static final PathId PATH_ID_6 = new PathId("6");

    private RerouteService rerouteService;
    private Flow regularFlow;
    private Flow pinnedFlow;
    private Flow oneSwitchFlow;
    private YFlow regularYFlow;
    private Flow subFlow;



    @Mock
    private TransactionManager transactionManager;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private YFlowRepository yFlowRepository;

    @Mock
    HaFlowRepository haFlowRepository;

    @Mock
    FlowPathRepository flowPathRepository;

    @Mock
    PathSegmentRepository pathSegmentRepository;

    @Mock
    PersistenceManager persistenceManager;

    @Mock
    MessageSender carrier;

    @BeforeEach
    public void setup() throws Throwable {
        lenient().doAnswer(invocation -> {
            TransactionCallbackWithoutResult<?> arg = invocation.getArgument(0);
            arg.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.<TransactionCallbackWithoutResult<?>>any());

        lenient().doAnswer(invocation -> {
            TransactionCallback<?, ?> arg = invocation.getArgument(0);
            return arg.doInTransaction();
        }).when(transactionManager).doInTransaction(Mockito.<TransactionCallback<?, ?>>any());

        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(repositoryFactory.createYFlowRepository()).thenReturn(yFlowRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createPathSegmentRepository()).thenReturn(pathSegmentRepository);
        when(repositoryFactory.createHaFlowRepository()).thenReturn(haFlowRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        rerouteService = new RerouteService(persistenceManager);

        pinnedFlow = buildPinnedFlow();

        regularFlow = Flow.builder().flowId(FLOW_ID).srcSwitch(SWITCH_A)
                .destSwitch(SWITCH_C).pinned(false)
                .priority(2)
                .build();
        FlowPath regularFlowForwardPath = FlowPath.builder().pathId(PATH_ID_3)
                .srcSwitch(SWITCH_A).destSwitch(SWITCH_C)
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 3))
                .status(FlowPathStatus.ACTIVE)
                .build();
        List<PathSegment> unpinnedFlowForwardSegments = new ArrayList<>();
        unpinnedFlowForwardSegments.add(PathSegment.builder()
                .pathId(regularFlowForwardPath.getPathId())
                .srcSwitch(SWITCH_A)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(1)
                .build());
        unpinnedFlowForwardSegments.add(PathSegment.builder()
                .pathId(regularFlowForwardPath.getPathId())
                .srcSwitch(SWITCH_B)
                .srcPort(2)
                .destSwitch(SWITCH_C)
                .destPort(1)
                .build());
        regularFlowForwardPath.setSegments(unpinnedFlowForwardSegments);

        FlowPath regularFlowReversePath = FlowPath.builder().pathId(PATH_ID_4)
                .srcSwitch(SWITCH_C).destSwitch(SWITCH_A)
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 3))
                .status(FlowPathStatus.ACTIVE)
                .build();
        List<PathSegment> unpinnedFlowReverseSegments = new ArrayList<>();
        unpinnedFlowReverseSegments.add(PathSegment.builder()
                .pathId(regularFlowReversePath.getPathId())
                .srcSwitch(SWITCH_C)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(2)
                .build());
        unpinnedFlowReverseSegments.add(PathSegment.builder()
                .pathId(regularFlowReversePath.getPathId())
                .srcSwitch(SWITCH_B)
                .srcPort(1)
                .destSwitch(SWITCH_A)
                .destPort(1)
                .build());
        regularFlowReversePath.setSegments(unpinnedFlowReverseSegments);
        regularFlow.setForwardPath(regularFlowForwardPath);
        regularFlow.setReversePath(regularFlowReversePath);

        oneSwitchFlow = buildOneSwitchFlow();

        regularYFlow = YFlow.builder()
                .yFlowId(YFLOW_ID)
                .priority(2)
                .sharedEndpoint(new SharedEndpoint(SWITCH_A.getSwitchId(), 10))
                .build();

        FlowPath regularYFlowForwardPath = FlowPath.builder().pathId(PATH_ID_3)
                .srcSwitch(SWITCH_A).destSwitch(SWITCH_C)
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 3))
                .status(FlowPathStatus.ACTIVE)
                .build();
        regularYFlowForwardPath.setSegments(unpinnedFlowForwardSegments);

        FlowPath regularYFlowReversePath = FlowPath.builder().pathId(PATH_ID_4)
                .srcSwitch(SWITCH_C).destSwitch(SWITCH_A)
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 3))
                .status(FlowPathStatus.ACTIVE)
                .build();
        regularYFlowReversePath.setSegments(unpinnedFlowReverseSegments);
        subFlow = Flow.builder()
                .flowId(SUB_YFLOW_ID)
                .srcSwitch(SWITCH_A)
                .destSwitch(SWITCH_C)
                .pinned(false)
                .priority(2)
                .yFlowId(YFLOW_ID)
                .yFlow(regularYFlow)
                .build();
        subFlow.setForwardPath(regularYFlowForwardPath);
        subFlow.setReversePath(regularYFlowReversePath);

        Set<YSubFlow> subFlows = Collections.singleton(YSubFlow.builder().yFlow(regularYFlow).flow(subFlow).build());
        regularYFlow.setSubFlows(subFlows);
    }


    @Test
    public void testRerouteInactivePinnedFlowsOneFailedSegment() {
        pinnedFlow.setStatus(FlowStatus.DOWN);
        setInactiveStatuses(pinnedFlow.getPaths(), SWITCH_ID_A, PORT);

        when(flowRepository.findInactiveFlows()).thenReturn(Collections.singletonList(pinnedFlow));
        doAnswer(invocation -> {
            FlowStatus status = invocation.getArgument(1);
            pinnedFlow.setStatus(status);
            return null;
        }).when(flowRepository).updateStatusSafe(eq(pinnedFlow), any(), any());

        when(haFlowRepository.findInactive()).thenReturn(new ArrayList<>());

        rerouteService.rerouteInactiveFlows(carrier, CORRELATION_ID, REROUTE_INACTIVE_FLOWS_COMMAND);
        assertEquals(FlowStatus.UP, pinnedFlow.getStatus());
        assertActivePathsAndSegments(pinnedFlow.getPaths());
    }

    @Test
    public void testRerouteInactivePinnedHaFlowsOneFailedSegment() {
        HaFlow pinnedHaFlow = buildDownPinnedHaFlow();
        for (HaFlowPath haFlowPath : pinnedHaFlow.getPaths()) {
            haFlowPath.setStatus(FlowPathStatus.INACTIVE);
            setInactiveStatuses(haFlowPath.getSubPaths(), SWITCH_ID_A, PORT);
        }

        when(flowRepository.findInactiveFlows()).thenReturn(new ArrayList<>());
        when(haFlowRepository.findInactive()).thenReturn(Collections.singletonList(pinnedHaFlow));
        doAnswer(invocation -> {
            FlowStatus status = invocation.getArgument(1);
            pinnedHaFlow.setStatus(status);
            return null;
        }).when(haFlowRepository).updateStatusSafe(eq(pinnedHaFlow), any(), any());

        rerouteService.rerouteInactiveFlows(carrier, CORRELATION_ID, REROUTE_INACTIVE_FLOWS_COMMAND);

        assertEquals(FlowStatus.UP, pinnedHaFlow.getStatus());
        for (HaSubFlow haSubFlow : pinnedHaFlow.getHaSubFlows()) {
            assertEquals(FlowStatus.UP, haSubFlow.getStatus());
        }
        for (HaFlowPath haFlowPath : pinnedHaFlow.getPaths()) {
            assertEquals(FlowPathStatus.ACTIVE, haFlowPath.getStatus());
            assertActivePathsAndSegments(haFlowPath.getSubPaths());
        }
    }

    @Test
    public void testRerouteInactivePinnedFlowsSeveralFailedSegments() {
        pinnedFlow.setStatus(FlowStatus.DOWN);
        setInactiveStatusesForAllSegments(pinnedFlow.getPaths());

        when(flowRepository.findInactiveFlows()).thenReturn(Collections.singletonList(pinnedFlow));
        when(haFlowRepository.findInactive()).thenReturn(new ArrayList<>());

        rerouteService.rerouteInactiveFlows(carrier, CORRELATION_ID, REROUTE_INACTIVE_FLOWS_COMMAND);

        verify(flowPathRepository, times(0)).updateStatus(any(), any());
        assertEquals(FlowStatus.DOWN, pinnedFlow.getStatus());
        assertInactivePathAndSegments(pinnedFlow.getPaths(), SWITCH_ID_A, PORT);
    }

    @Test
    public void testRerouteInactivePinnedHaFlowsSeveralFailedSegments() {
        HaFlow pinnedHaFlow = buildDownPinnedHaFlow();
        for (HaFlowPath haFlowPath : pinnedHaFlow.getPaths()) {
            haFlowPath.setStatus(FlowPathStatus.INACTIVE);
            setInactiveStatusesForAllSegments(haFlowPath.getSubPaths());
        }

        when(flowRepository.findInactiveFlows()).thenReturn(new ArrayList<>());
        when(haFlowRepository.findInactive()).thenReturn(Collections.singletonList(pinnedHaFlow));

        rerouteService.rerouteInactiveFlows(carrier, CORRELATION_ID, REROUTE_INACTIVE_FLOWS_COMMAND);

        assertEquals(FlowStatus.DOWN, pinnedHaFlow.getStatus());
        for (HaSubFlow haSubFlow : pinnedHaFlow.getHaSubFlows()) {
            assertEquals(FlowStatus.DOWN, haSubFlow.getStatus());
        }
        for (HaFlowPath path : pinnedHaFlow.getPaths()) {
            assertEquals(FlowPathStatus.INACTIVE, path.getStatus());
            assertInactivePathAndSegments(path.getSubPaths(), SWITCH_ID_A, PORT);
        }
    }

    @Test
    public void handlePathNotFoundException() {
        PathNode islSide = new PathNode(SWITCH_A.getSwitchId(), 1, 0);

        when(flowPathRepository.findBySegmentEndpoint(eq(islSide.getSwitchId()), eq(islSide.getPortNo())))
                .thenReturn(asList(regularFlow.getForwardPath(), regularFlow.getReversePath()));

        RerouteAffectedFlows request = new RerouteAffectedFlows(islSide, "dummy-reason - unittest");
        rerouteService.rerouteAffectedFlows(carrier, CORRELATION_ID, request);

        verify(flowRepository).updateStatusSafe(eq(regularFlow), eq(FlowStatus.DOWN), any());
        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(regularFlow.getPriority())
                .timeCreate(regularFlow.getTimeCreate())
                .affectedIsl(Collections.singleton(new IslEndpoint(islSide.getSwitchId(), islSide.getPortNo())))
                .effectivelyDown(true)
                .reason(request.getReason())
                .flowType(FlowType.FLOW)
                .build();
        verify(carrier).emitRerouteCommand(eq(regularFlow.getFlowId()), eq(expected));
    }

    @Test
    public void handlePathNoFoundExceptionForSubYFlow() {
        PathNode islSide = new PathNode(SWITCH_A.getSwitchId(), 1, 0);

        when(flowPathRepository.findBySegmentEndpoint(eq(islSide.getSwitchId()), eq(islSide.getPortNo())))
                .thenReturn(asList(subFlow.getForwardPath(), subFlow.getReversePath()));

        RerouteAffectedFlows request = new RerouteAffectedFlows(islSide, "dummy-reason - unittest");
        rerouteService.rerouteAffectedFlows(carrier, CORRELATION_ID, request);

        verify(flowRepository).updateStatusSafe(eq(subFlow), eq(FlowStatus.DOWN), any());
        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(regularYFlow.getPriority())
                .timeCreate(regularYFlow.getTimeCreate())
                .affectedIsl(Collections.singleton(new IslEndpoint(islSide.getSwitchId(), islSide.getPortNo())))
                .effectivelyDown(true)
                .reason(request.getReason())
                .flowType(FlowType.Y_FLOW)
                .build();
        verify(carrier).emitRerouteCommand(eq(regularYFlow.getYFlowId()), eq(expected));
    }

    @Test
    public void rerouteAffectedHaFlows() {
        HaFlow haFlow = buildHaFlow();
        PathNode islSide = new PathNode(SWITCH_A.getSwitchId(), 1, 0);

        List<FlowPath> subFlow1Paths = getSubFlowPaths(haFlow, HA_SUB_FLOW_ID_1);
        assertEquals(2, subFlow1Paths.size());
        when(flowPathRepository.findBySegmentEndpoint(eq(islSide.getSwitchId()), eq(islSide.getPortNo())))
                .thenReturn(subFlow1Paths);

        RerouteAffectedFlows request = new RerouteAffectedFlows(islSide, "dummy-reason - unittest");
        rerouteService.rerouteAffectedFlows(carrier, CORRELATION_ID, request);

        verify(flowRepository, times(0)).updateStatusSafe(any(), any(), any());
        verify(haFlowRepository, times(1)).updateStatusSafe(eq(haFlow), eq(FlowStatus.DOWN), any());
        assertEquals(FlowStatus.DOWN, haFlow.getHaSubFlow(HA_SUB_FLOW_ID_1).get().getStatus());
        assertEquals(FlowStatus.UP, haFlow.getHaSubFlow(HA_SUB_FLOW_ID_2).get().getStatus());
        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(haFlow.getPriority())
                .timeCreate(haFlow.getTimeCreate())
                .affectedIsl(Collections.singleton(new IslEndpoint(islSide.getSwitchId(), islSide.getPortNo())))
                .effectivelyDown(true)
                .reason(request.getReason())
                .flowType(FlowType.HA_FLOW)
                .build();
        verify(carrier, times(1)).emitRerouteCommand(eq(haFlow.getHaFlowId()), eq(expected));
    }

    @Test
    public void handleUpdateSingleSwitchFlows() {
        when(flowRepository.findOneSwitchFlows(oneSwitchFlow.getSrcSwitch().getSwitchId()))
                .thenReturn(Collections.singletonList(oneSwitchFlow));

        rerouteService.processSingleSwitchFlowStatusUpdate(
                new SwitchStateChanged(oneSwitchFlow.getSrcSwitchId(), SwitchStatus.INACTIVE));

        assertEquals(FlowStatus.DOWN, oneSwitchFlow.getStatus(),
                format("Switch %s is inactive", oneSwitchFlow.getSrcSwitchId()));
    }

    @Test
    public void shouldSkipRerouteRequestsForFlowWithoutAffectedPathSegment() {
        PathNode islSide = new PathNode(SWITCH_A.getSwitchId(), 1, 0);

        when(flowPathRepository.findBySegmentEndpoint(eq(islSide.getSwitchId()), eq(islSide.getPortNo())))
                .thenReturn(asList(regularFlow.getForwardPath(), regularFlow.getReversePath()));

        doThrow(new EntityNotFoundException("Not found"))
                .when(pathSegmentRepository).updateFailedStatus(any(), any(), anyBoolean());

        RerouteAffectedFlows request = new RerouteAffectedFlows(islSide, "dummy-reason - unittest");
        rerouteService.rerouteAffectedFlows(carrier, CORRELATION_ID, request);

        verifyNoInteractions(carrier);
    }

    @Test
    public void handleRerouteInactiveAffectedFlows() {
        when(flowPathRepository.findInactiveBySegmentSwitch(regularFlow.getSrcSwitchId()))
                .thenReturn(asList(regularFlow.getForwardPath(), regularFlow.getReversePath()));

        regularFlow.setStatus(FlowStatus.DOWN);
        rerouteService.rerouteInactiveAffectedFlows(carrier, CORRELATION_ID, regularFlow.getSrcSwitchId());

        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(regularFlow.getPriority())
                .timeCreate(regularFlow.getTimeCreate())
                .affectedIsl(Collections.emptySet())
                .effectivelyDown(true)
                .flowType(FlowType.FLOW)
                .reason(format("Switch '%s' online", regularFlow.getSrcSwitchId()))
                .build();
        verify(carrier).emitRerouteCommand(eq(regularFlow.getFlowId()), eq(expected));

        regularFlow.setStatus(FlowStatus.UP);
    }

    @Test
    public void handleRerouteAffectedYFlows() {
        when(flowPathRepository.findInactiveBySegmentSwitch(subFlow.getSrcSwitchId()))
                .thenReturn(asList(subFlow.getForwardPath(), subFlow.getReversePath()));

        subFlow.setStatus(FlowStatus.DOWN);
        rerouteService.rerouteInactiveAffectedFlows(carrier, CORRELATION_ID, subFlow.getSrcSwitchId());

        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(regularYFlow.getPriority())
                .timeCreate(regularYFlow.getTimeCreate())
                .affectedIsl(Collections.emptySet())
                .effectivelyDown(true)
                .reason(format("Switch '%s' online", subFlow.getSrcSwitchId()))
                .flowType(FlowType.Y_FLOW)
                .build();
        verify(carrier).emitRerouteCommand(eq(regularYFlow.getYFlowId()), eq(expected));

        regularFlow.setStatus(FlowStatus.UP);
    }

    @Test
    public void handleInactiveAffectedHaFlows() {
        HaFlow haFlow = buildHaFlow();
        PathNode islSide = new PathNode(SWITCH_A.getSwitchId(), 1, 0);

        when(flowPathRepository.findInactiveBySegmentSwitch(eq(islSide.getSwitchId())))
                .thenReturn(haFlow.getSubPaths());

        for (HaSubFlow haSubFlow : haFlow.getHaSubFlows()) {
            haSubFlow.setStatus(FlowStatus.DOWN);
        }
        haFlow.setStatus(FlowStatus.DOWN);
        rerouteService.rerouteInactiveAffectedFlows(carrier, CORRELATION_ID, islSide.getSwitchId());

        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(haFlow.getPriority())
                .timeCreate(haFlow.getTimeCreate())
                .affectedIsl(Collections.emptySet())
                .effectivelyDown(true)
                .reason(format("Switch '%s' online", islSide.getSwitchId()))
                .flowType(FlowType.HA_FLOW)
                .build();
        verify(carrier, times(1)).emitRerouteCommand(eq(haFlow.getHaFlowId()), eq(expected));
    }

    @Test
    public void processManualRerouteRequest() {
        when(flowRepository.findById(regularFlow.getFlowId()))
                .thenReturn(Optional.of(regularFlow));

        FlowRerouteRequest request = new FlowRerouteRequest(regularFlow.getFlowId(), true, false,
                Collections.emptySet(), "reason", true);
        rerouteService.processRerouteRequest(carrier, CORRELATION_ID, request);

        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(regularFlow.getPriority())
                .timeCreate(regularFlow.getTimeCreate())
                .affectedIsl(Collections.emptySet())
                .effectivelyDown(true)
                .flowType(FlowType.FLOW)
                .reason("reason")
                .build();
        verify(carrier).emitManualRerouteCommand(eq(regularFlow.getFlowId()), eq(expected));
    }

    @Test
    public void processManualRerouteRequestForYFlow() {
        when(yFlowRepository.findById(YFLOW_ID)).thenReturn(Optional.of(regularYFlow));

        YFlowRerouteRequest request = new YFlowRerouteRequest(regularYFlow.getYFlowId(), Collections.emptySet(),
                "reason", false);
        rerouteService.processRerouteRequest(carrier, CORRELATION_ID, request);

        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(regularYFlow.getPriority())
                .timeCreate(regularYFlow.getTimeCreate())
                .affectedIsl(Collections.emptySet())
                .reason("reason")
                .flowType(FlowType.Y_FLOW)
                .build();
        verify(carrier).emitManualRerouteCommand(eq(regularYFlow.getYFlowId()), eq(expected));
    }

    @Test
    public void processManualRerouteRequestForHaFlow() {
        HaFlow haFlow = buildHaFlow();
        when(haFlowRepository.findById(haFlow.getHaFlowId())).thenReturn(Optional.of(haFlow));

        HaFlowRerouteRequest request = new HaFlowRerouteRequest(haFlow.getHaFlowId(), Collections.emptySet(),
                false, "reason", false, true);
        rerouteService.processRerouteRequest(carrier, CORRELATION_ID, request);

        FlowThrottlingData expected = FlowThrottlingData.builder()
                .correlationId(CORRELATION_ID)
                .priority(haFlow.getPriority())
                .timeCreate(haFlow.getTimeCreate())
                .affectedIsl(Collections.emptySet())
                .reason("reason")
                .flowType(FlowType.HA_FLOW)
                .build();
        verify(carrier).emitManualRerouteCommand(eq(haFlow.getHaFlowId()), eq(expected));
    }

    private static Flow buildOneSwitchFlow() {
        Flow oneSwitchFlow = Flow.builder().flowId(FLOW_ID).srcSwitch(SWITCH_A)
                .destSwitch(SWITCH_A)
                .build();
        FlowPath oneSwitchFlowForwardPath = FlowPath.builder().pathId(PATH_ID_5)
                .srcSwitch(SWITCH_A).destSwitch(SWITCH_A)
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 4))
                .status(FlowPathStatus.ACTIVE)
                .build();
        FlowPath oneSwitchFlowReversePath = FlowPath.builder().pathId(PATH_ID_6)
                .srcSwitch(SWITCH_A).destSwitch(SWITCH_A)
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 4))
                .status(FlowPathStatus.ACTIVE)
                .build();
        oneSwitchFlow.setForwardPath(oneSwitchFlowForwardPath);
        oneSwitchFlow.setReversePath(oneSwitchFlowReversePath);
        return oneSwitchFlow;
    }

    private static Flow buildPinnedFlow() {
        Flow pinnedFlow = Flow.builder().flowId(FLOW_ID).srcSwitch(SWITCH_A)
                .destSwitch(SWITCH_C).pinned(true).build();
        FlowPath pinnedFlowForwardPath = FlowPath.builder()
                .pathId(PATH_ID_1)
                .srcSwitch(SWITCH_A).destSwitch(SWITCH_C)
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 1))
                .build();
        List<PathSegment> pinnedFlowForwardSegments = new ArrayList<>();
        pinnedFlowForwardSegments.add(PathSegment.builder()
                .pathId(pinnedFlowForwardPath.getPathId())
                .srcSwitch(SWITCH_A)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(1)
                .build());
        pinnedFlowForwardSegments.add(PathSegment.builder()
                .pathId(pinnedFlowForwardPath.getPathId())
                .srcSwitch(SWITCH_B)
                .srcPort(2)
                .destSwitch(SWITCH_C)
                .destPort(1)
                .build());
        pinnedFlowForwardPath.setSegments(pinnedFlowForwardSegments);

        FlowPath pinnedFlowReversePath = FlowPath.builder().pathId(PATH_ID_2)
                .srcSwitch(SWITCH_C).destSwitch(SWITCH_A)
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 2))
                .build();
        List<PathSegment> pinnedFlowReverseSegments = new ArrayList<>();
        pinnedFlowReverseSegments.add(PathSegment.builder()
                .pathId(pinnedFlowReversePath.getPathId())
                .srcSwitch(SWITCH_C)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(2)
                .build());
        pinnedFlowReverseSegments.add(PathSegment.builder()
                .pathId(pinnedFlowReversePath.getPathId())
                .srcSwitch(SWITCH_B)
                .srcPort(1)
                .destSwitch(SWITCH_A)
                .destPort(1)
                .build());
        pinnedFlowReversePath.setSegments(pinnedFlowReverseSegments);
        pinnedFlow.setForwardPath(pinnedFlowForwardPath);
        pinnedFlow.setReversePath(pinnedFlowReversePath);
        return pinnedFlow;
    }

    private static HaSubFlow buildHaSubFlow(String flowId, Switch sw) {
        return HaSubFlow.builder()
                .haSubFlowId(flowId)
                .endpointSwitch(sw)
                .build();
    }

    private static PathSegment buildSegment(
            PathId pathId, Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort) {
        return PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .build();
    }

    private static FlowPath buildFlowPath(
            PathId pathId, HaSubFlow haSubFlow, FlowSegmentCookie cookie, Switch srcSwitch, Switch transitSwitch,
            Switch dstSwitch) {
        FlowPath path = FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .cookie(cookie)
                .status(FlowPathStatus.ACTIVE)
                .build();
        path.setSegments(newArrayList(
                buildSegment(pathId, srcSwitch, PORT, transitSwitch, 2),
                buildSegment(pathId, transitSwitch, 3, dstSwitch, 4)));
        path.setHaSubFlow(haSubFlow);
        return path;
    }

    private static HaFlow buildHaFlow() {
        HaFlow haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID)
                .priority(2)
                .sharedSwitch(SWITCH_A)
                .build();

        List<HaSubFlow> haSubFlows = newArrayList(
                buildHaSubFlow(HA_SUB_FLOW_ID_1, SWITCH_C),
                buildHaSubFlow(HA_SUB_FLOW_ID_2, SWITCH_D));

        haFlow.setHaSubFlows(haSubFlows);

        int baseCookie = 4;
        HaFlowPath forwardPath = HaFlowPath.builder().haPathId(PATH_ID_1)
                .sharedSwitch(haFlow.getSharedSwitch())
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, baseCookie))
                .status(FlowPathStatus.ACTIVE)
                .build();
        forwardPath.setHaSubFlows(haSubFlows);
        forwardPath.setSubPaths(newArrayList(
                buildFlowPath(PATH_ID_2, haSubFlows.get(0),
                        forwardPath.getCookie().toBuilder().subType(FlowSubType.HA_SUB_FLOW_1).build(),
                        SWITCH_A, SWITCH_B, SWITCH_C),
                buildFlowPath(PATH_ID_3, haSubFlows.get(1),
                        forwardPath.getCookie().toBuilder().subType(FlowSubType.HA_SUB_FLOW_2).build(),
                        SWITCH_A, SWITCH_B, SWITCH_D)));

        HaFlowPath reversePath = HaFlowPath.builder().haPathId(PATH_ID_4)
                .sharedSwitch(haFlow.getSharedSwitch())
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, baseCookie))
                .status(FlowPathStatus.ACTIVE)
                .build();
        reversePath.setHaSubFlows(haSubFlows);
        reversePath.setSubPaths(newArrayList(
                buildFlowPath(PATH_ID_5, haSubFlows.get(0),
                        reversePath.getCookie().toBuilder().subType(FlowSubType.HA_SUB_FLOW_1).build(),
                        SWITCH_C, SWITCH_B, SWITCH_A),
                buildFlowPath(PATH_ID_6, haSubFlows.get(1),
                        reversePath.getCookie().toBuilder().subType(FlowSubType.HA_SUB_FLOW_2).build(),
                        SWITCH_D, SWITCH_B, SWITCH_A)));

        haFlow.setForwardPath(forwardPath);
        haFlow.setReversePath(reversePath);
        return haFlow;
    }

    private static HaFlow buildDownPinnedHaFlow() {
        HaFlow pinnedHaFlow = buildHaFlow();
        pinnedHaFlow.setPinned(true);
        pinnedHaFlow.setStatus(FlowStatus.DOWN);
        for (HaSubFlow haSubFlow : pinnedHaFlow.getHaSubFlows()) {
            haSubFlow.setStatus(FlowStatus.DOWN);
        }
        return pinnedHaFlow;
    }

    private static void assertInactivePathAndSegments(
            Collection<FlowPath> paths, SwitchId activeSwitch, int activePort) {
        for (FlowPath path : paths) {
            assertEquals(FlowPathStatus.INACTIVE, path.getStatus());
            for (PathSegment ps : path.getSegments()) {
                if (ps.containsNode(activeSwitch, activePort)) {
                    assertFalse(ps.isFailed());
                } else {
                    assertTrue(ps.isFailed());
                }
            }
        }
    }

    private static void assertActivePathsAndSegments(Collection<FlowPath> paths) {
        for (FlowPath path : paths) {
            assertEquals(FlowPathStatus.ACTIVE, path.getStatus());
            for (PathSegment ps : path.getSegments()) {
                if (ps.containsNode(SWITCH_ID_A, PORT)) {
                    assertFalse(ps.isFailed());
                }
            }
        }
    }

    private static void setInactiveStatuses(Collection<FlowPath> paths, SwitchId failedSwitchId, int failedPort) {
        for (FlowPath path : paths) {
            path.setStatus(FlowPathStatus.INACTIVE);
            for (PathSegment pathSegment : path.getSegments()) {
                if (pathSegment.containsNode(failedSwitchId, failedPort)) {
                    pathSegment.setFailed(true);
                }
            }
        }
    }


    private static void setInactiveStatusesForAllSegments(Collection<FlowPath> paths) {
        for (FlowPath path : paths) {
            path.setStatus(FlowPathStatus.INACTIVE);
            for (PathSegment pathSegment : path.getSegments()) {
                pathSegment.setFailed(true);
            }
        }
    }

    private static List<FlowPath> getSubFlowPaths(HaFlow haFlow, String subFlowId) {
        return haFlow.getSubPaths().stream()
                .filter(path -> path.getHaSubFlowId().equals(subFlowId)).collect(Collectors.toList());
    }
}
