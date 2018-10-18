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
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flow.service.FlowService.ReroutedFlow;
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

    @Before
    public void setUp() {
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        Switch switch1 = Switch.builder().switchId(SWITCH_ID_1).build();
        switchRepository.createOrUpdate(switch1);

        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        switchRepository.createOrUpdate(switch2);

        Switch switch3 = Switch.builder().switchId(SWITCH_ID_3).build();
        switchRepository.createOrUpdate(switch3);
        IslRepository islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        Isl isl1To2 = new Isl();
        isl1To2.setSrcSwitch(switch1);
        isl1To2.setSrcPort(11);
        isl1To2.setDestSwitch(switch2);
        isl1To2.setDestPort(11);
        islRepository.createOrUpdate(isl1To2);

        Isl isl2To1 = new Isl();
        isl2To1.setSrcSwitch(switch2);
        isl2To1.setSrcPort(11);
        isl2To1.setDestSwitch(switch1);
        isl2To1.setDestPort(11);
        islRepository.createOrUpdate(isl2To1);

        Isl isl2To3 = new Isl();
        isl2To3.setSrcSwitch(switch2);
        isl2To3.setSrcPort(12);
        isl2To3.setDestSwitch(switch3);
        isl2To3.setDestPort(11);
        islRepository.createOrUpdate(isl2To3);

        Isl isl3To2 = new Isl();
        isl3To2.setSrcSwitch(switch3);
        isl3To2.setSrcPort(11);
        isl3To2.setDestSwitch(switch2);
        isl3To2.setDestPort(12);
        islRepository.createOrUpdate(isl3To2);


        Isl isl1To3 = new Isl();
        isl1To3.setSrcSwitch(switch1);
        isl1To3.setSrcPort(11);
        isl1To3.setDestSwitch(switch3);
        isl1To3.setDestPort(11);
        islRepository.createOrUpdate(isl1To3);

        Isl isl3To1 = new Isl();
        isl3To1.setSrcSwitch(switch3);
        isl3To1.setSrcPort(11);
        isl3To1.setDestSwitch(switch1);
        isl3To1.setDestPort(11);
        islRepository.createOrUpdate(isl3To1);
    }

    @Test
    public void shouldRerouteFlow() throws RecoverableException, UnroutableFlowException,
            FlowNotFoundException, FlowAlreadyExistException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {
        PathComputer pathComputer = mock(PathComputer.class);
        PathComputerFactory pathComputerFactory = mock(PathComputerFactory.class);
        FlowValidator flowValidator = new FlowValidator(persistenceManager.getRepositoryFactory());
        when(pathComputerFactory.getPathComputer()).thenReturn(pathComputer);

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowService flowService = new FlowService(persistenceManager,
                pathComputerFactory, new FlowResourcesManager(persistenceManager, flowResourcesConfig), flowValidator);

        String flowId = "test-flow";
        FlowPair flowPair = new FlowPair(flowId,
                getOrCreateSwitch(SWITCH_ID_1), 1, 101,
                getOrCreateSwitch(SWITCH_ID_3), 2, 102);
        flowPair.getForward().setBandwidth(0);
        flowPair.setStatus(FlowStatus.IN_PROGRESS);

        when(pathComputer.getPath(any())).thenReturn(PATH_DIRECT_1_TO_3);

        flowService.createFlow(flowPair.getForward(), null, mock(FlowCommandSender.class));
        flowService.updateFlowStatus(flowId, FlowStatus.UP);

        when(pathComputer.getPath(any(), eq(true))).thenReturn(PATH_1_TO_3_VIA_2);

        ReroutedFlow reroutedFlow = flowService.rerouteFlow(flowId, true, mock(FlowCommandSender.class));
        assertNotNull(reroutedFlow);
        checkSamePaths(PATH_1_TO_3_VIA_2.getForward(), reroutedFlow.getNewFlow().getForward().getFlowPath());

        Optional<FlowPair> foundFlow = persistenceManager.getRepositoryFactory().createFlowPairRepository()
                .findById(flowId);
        assertEquals(flowPair.getForward().getFlowId(), foundFlow.get().getForward().getFlowId());
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

    private Switch getOrCreateSwitch(SwitchId switchId) {
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        return switchRepository.findById(switchId).orElseGet(() -> {
            Switch sw = Switch.builder().switchId(switchId).status(SwitchStatus.ACTIVE).build();
            switchRepository.createOrUpdate(sw);
            return sw;
        });
    }
}
