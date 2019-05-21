/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.flow.service;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.ReroutedFlowPaths;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

public class FlowServiceTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final PathPair PATH_DIRECT_1_TO_3 = PathPair.builder()
            .forward(Path.builder().srcSwitchId(SWITCH_ID_1).destSwitchId(SWITCH_ID_3).latency(1).segments(asList(
                    Path.Segment.builder().srcSwitchId(SWITCH_ID_1).srcPort(11).latency(1L)
                            .destSwitchId(SWITCH_ID_3).destPort(11).build())).build())
            .reverse(Path.builder().srcSwitchId(SWITCH_ID_3).destSwitchId(SWITCH_ID_1).latency(1).segments(asList(
                    Path.Segment.builder().srcSwitchId(SWITCH_ID_3).srcPort(11).latency(1L)
                            .destSwitchId(SWITCH_ID_1).destPort(11).build())).build()).build();

    private static final PathPair PATH_1_TO_3_VIA_2 = PathPair.builder()
            .forward(Path.builder().srcSwitchId(SWITCH_ID_1).destSwitchId(SWITCH_ID_3).latency(2).segments(asList(
                    Path.Segment.builder().srcSwitchId(SWITCH_ID_1).srcPort(11).latency(1L)
                            .destSwitchId(SWITCH_ID_2).destPort(11).build(),
                    Path.Segment.builder().srcSwitchId(SWITCH_ID_2).srcPort(12).latency(1L)
                            .destSwitchId(SWITCH_ID_3).destPort(11).build()))
                    .build())
            .reverse(Path.builder().srcSwitchId(SWITCH_ID_3).destSwitchId(SWITCH_ID_1).latency(2).segments(asList(
                    Path.Segment.builder().srcSwitchId(SWITCH_ID_3).srcPort(11).latency(1L)
                            .destSwitchId(SWITCH_ID_2).destPort(12).build(),
                    Path.Segment.builder().srcSwitchId(SWITCH_ID_2).srcPort(11).latency(1L)
                            .destSwitchId(SWITCH_ID_1).destPort(11).build()))
                    .build()).build();
    private static String FLOW_ID = "test-flow";

    private FlowService flowService;
    private PathComputer pathComputer;

    private static long BANDWIDTH = 1000L;

    private SwitchRepository switchRepository;
    private IslRepository islRepository;
    private FlowRepository flowRepository;
    private FlowPathRepository flowPathRepository;


    @Before
    public void setUp() {
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();

        pathComputer = mock(PathComputer.class);
        PathComputerFactory pathComputerFactory = mock(PathComputerFactory.class);
        FlowValidator flowValidator = new FlowValidator(persistenceManager.getRepositoryFactory());
        when(pathComputerFactory.getPathComputer()).thenReturn(pathComputer);

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        flowService = new FlowService(persistenceManager,
                pathComputerFactory, new FlowResourcesManager(persistenceManager, flowResourcesConfig), flowValidator,
                new FlowCommandFactory());

        createTopology();
    }

    @Test
    public void shouldUpdateFlowStatus() {
        String flowId = "test-flow";
        Flow flow = new TestFlowBuilder(flowId)
                .srcSwitch(getOrCreateSwitch(SWITCH_ID_1))
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(getOrCreateSwitch(SWITCH_ID_2))
                .destPort(2)
                .destVlan(102)
                .status(FlowStatus.IN_PROGRESS)
                .build();

        flowRepository.createOrUpdate(flow);

        flowService.updateFlowStatus(flowId, FlowStatus.UP, emptySet());

        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        assertEquals(FlowStatus.UP, foundFlow.get().getStatus());
    }

    @Test
    public void shouldSurviveResourceAllocationFailureOnCreateFlowWithProtectedPath() throws RecoverableException,
            UnroutableFlowException, FlowNotFoundException, FlowAlreadyExistException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {

        FlowService flowServiceSpy = Mockito.spy(flowService);
        Mockito.doThrow(ResourceAllocationException.class).doCallRealMethod()
                .when(flowServiceSpy).createProtectedPath(any(), any());

        Flow flow = getFlowBuilder()
                .allocateProtectedPath(true)
                .build();

        when(pathComputer.getPath(any()))
                .thenReturn(PATH_DIRECT_1_TO_3)
                .thenReturn(PATH_1_TO_3_VIA_2);

        flowService.createFlow(flow, null, mock(FlowCommandSender.class));

        Optional<Flow> foundFlow = persistenceManager.getRepositoryFactory().createFlowRepository()
                .findById(FLOW_ID);
        assertEquals(flow.getFlowId(), foundFlow.get().getFlowId());
    }

    @Test
    public void shouldRerouteFlow() throws RecoverableException, UnroutableFlowException,
            FlowNotFoundException, FlowAlreadyExistException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {

        Flow flow = getFlowBuilder().build();

        when(pathComputer.getPath(any())).thenReturn(PATH_DIRECT_1_TO_3);

        flowService.createFlow(flow, null, mock(FlowCommandSender.class));
        flowService.updateFlowStatus(FLOW_ID, FlowStatus.UP, emptySet());

        when(pathComputer.getPath(any(), anyList())).thenReturn(PATH_1_TO_3_VIA_2);

        ReroutedFlowPaths reroutedFlowPaths =
                flowService.rerouteFlow(FLOW_ID, true, emptySet(), mock(FlowCommandSender.class));

        assertNotNull(reroutedFlowPaths);
        assertTrue(reroutedFlowPaths.isRerouted());
        checkSamePaths(PATH_1_TO_3_VIA_2.getForward(), reroutedFlowPaths.getNewFlowPaths().getForwardPath());

        Optional<FlowPair> foundFlow = persistenceManager.getRepositoryFactory().createFlowPairRepository()
                .findById(FLOW_ID);
        assertEquals(flow.getFlowId(), foundFlow.get().getForward().getFlowId());
    }

    @Test
    public void shouldNotUpdatePathsOnRerouteWithProtectedFlowIfNoOtherPaths() throws RecoverableException,
            UnroutableFlowException, FlowNotFoundException, FlowAlreadyExistException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {

        Flow flow = getFlowBuilder()
                .allocateProtectedPath(true)
                .build();

        when(pathComputer.getPath(any()))
                .thenReturn(PATH_DIRECT_1_TO_3)
                .thenReturn(PATH_1_TO_3_VIA_2);

        flowService.createFlow(flow, null, mock(FlowCommandSender.class));
        flowService.updateFlowStatus(FLOW_ID, FlowStatus.UP, emptySet());

        Assert.assertThat(flowPathRepository.findAll(), hasSize(4));

        when(pathComputer.getPath(any(), anyList())).thenReturn(PATH_DIRECT_1_TO_3);
        when(pathComputer.getPath(any()))
                .thenReturn(PATH_1_TO_3_VIA_2);

        ReroutedFlowPaths reroutedFlowPaths =
                flowService.rerouteFlow(FLOW_ID, false, emptySet(), mock(FlowCommandSender.class));

        FlowPathsWithEncapsulation newFlowPaths = reroutedFlowPaths.getNewFlowPaths();
        FlowPathsWithEncapsulation oldFlowPaths = reroutedFlowPaths.getOldFlowPaths();

        assertEquals(newFlowPaths.getForwardPath(), oldFlowPaths.getForwardPath());
        assertEquals(newFlowPaths.getReversePath(), oldFlowPaths.getReversePath());
        assertEquals(newFlowPaths.getProtectedForwardPath(), oldFlowPaths.getProtectedForwardPath());
        assertEquals(newFlowPaths.getProtectedReversePath(), oldFlowPaths.getProtectedReversePath());

        Optional<Flow> foundFlow = persistenceManager.getRepositoryFactory().createFlowRepository()
                .findById(FLOW_ID);
        assertEquals(flow.getFlowId(), foundFlow.get().getFlowId());
    }

    private void checkSamePaths(Path path, FlowPath flowPath) {
        assertEquals(path.getSrcSwitchId(), flowPath.getSrcSwitch().getSwitchId());
        assertEquals(path.getDestSwitchId(), flowPath.getDestSwitch().getSwitchId());
        Path.Segment[] flowPathSegments = flowPath.getSegments().stream()
                .map(fp -> Path.Segment.builder()
                        .srcSwitchId(fp.getSrcSwitch().getSwitchId())
                        .srcPort(fp.getSrcPort())
                        .destSwitchId(fp.getDestSwitch().getSwitchId())
                        .destPort(fp.getDestPort())
                        .latency(fp.getLatency())
                        .build())
                .toArray(Path.Segment[]::new);
        assertThat(path.getSegments(), containsInAnyOrder(flowPathSegments));
    }

    private Flow.FlowBuilder getFlowBuilder() {
        return Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(getOrCreateSwitch(SWITCH_ID_1)).srcPort(1).srcVlan(101)
                .destSwitch(getOrCreateSwitch(SWITCH_ID_3)).destPort(2).destVlan(102)
                .bandwidth(BANDWIDTH)
                .status(FlowStatus.IN_PROGRESS);
    }

    private void createTopology() {
        Switch switch1 = getOrCreateSwitch(SWITCH_ID_1);
        Switch switch2 = getOrCreateSwitch(SWITCH_ID_2);
        Switch switch3 = getOrCreateSwitch(SWITCH_ID_3);

        createIsl(switch1, 11, switch2, 11);
        createIsl(switch2, 11, switch1, 11);
        createIsl(switch2, 12, switch3, 12);
        createIsl(switch3, 12, switch2, 12);
        createIsl(switch1, 13, switch3, 13);
        createIsl(switch3, 13, switch1, 13);
    }

    private Switch getOrCreateSwitch(SwitchId switchId) {
        return switchRepository.findById(switchId).orElseGet(() -> {
            Switch sw = Switch.builder().switchId(switchId).status(SwitchStatus.ACTIVE).build();
            switchRepository.createOrUpdate(sw);
            return sw;
        });
    }

    private Isl createIsl(Switch srcSwitch, int srcPort, Switch destSwitch, int destPort) {
        Isl isl = new Isl();
        isl.setSrcSwitch(srcSwitch);
        isl.setSrcPort(srcPort);
        isl.setDestSwitch(destSwitch);
        isl.setDestPort(destPort);
        isl.setMaxBandwidth(BANDWIDTH);
        islRepository.createOrUpdate(isl);

        return isl;
    }
}
