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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flow.model.ReroutedFlow;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import org.junit.Before;
import org.junit.Test;

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

    private SwitchRepository switchRepository;
    private IslRepository islRepository;
    private FlowRepository flowRepository;

    private PathComputer pathComputer;
    private FlowService flowService;

    @Before
    public void setUp() {
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();

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

        flowService.updateFlowStatus(flowId, FlowStatus.UP);

        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        assertEquals(FlowStatus.UP, foundFlow.get().getStatus());
    }

    @Test
    public void shouldRerouteFlow() throws RecoverableException, UnroutableFlowException,
            FlowNotFoundException, FlowAlreadyExistException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {

        String flowId = "test-flow";
        UnidirectionalFlow flow = new TestFlowBuilder(flowId)
                .srcSwitch(getOrCreateSwitch(SWITCH_ID_1))
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(getOrCreateSwitch(SWITCH_ID_3))
                .destPort(2)
                .destVlan(102)
                .bandwidth(0)
                .buildUnidirectionalFlow();
        flow.setStatus(FlowStatus.IN_PROGRESS);

        when(pathComputer.getPath(any())).thenReturn(PATH_DIRECT_1_TO_3);

        flowService.createFlow(flow.getFlow(), null, mock(FlowCommandSender.class));
        flowService.updateFlowStatus(flowId, FlowStatus.UP);

        when(pathComputer.getPath(any(), eq(true))).thenReturn(PATH_1_TO_3_VIA_2);

        ReroutedFlow reroutedFlow = flowService.rerouteFlow(flowId, true, mock(FlowCommandSender.class));
        assertNotNull(reroutedFlow);
        checkSamePaths(PATH_1_TO_3_VIA_2.getForward(), reroutedFlow.getNewFlow().getFlowPath());

        Optional<FlowPair> foundFlow = persistenceManager.getRepositoryFactory().createFlowPairRepository()
                .findById(flowId);
        assertEquals(flow.getFlowId(), foundFlow.get().getForward().getFlowId());
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
        islRepository.createOrUpdate(isl);

        return isl;
    }
}
