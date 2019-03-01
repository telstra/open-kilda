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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.cache.ResourceCache;
import org.openkilda.wfm.topology.flow.service.FlowService.ReroutedFlow;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import org.junit.Before;
import org.junit.Test;

public class FlowServiceTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final PathPair PATH_DIRECT_1_TO_3 = PathPair.builder()
            .forward(FlowPath.builder().latency(1).nodes(asList(
                    FlowPath.Node.builder().seqId(0).switchId(SWITCH_ID_1).portNo(11).segmentLatency(1L).build(),
                    FlowPath.Node.builder().seqId(1).switchId(SWITCH_ID_3).portNo(11).build())).build())
            .reverse(FlowPath.builder().latency(1).nodes(asList(
                    FlowPath.Node.builder().seqId(0).switchId(SWITCH_ID_3).portNo(11).segmentLatency(1L).build(),
                    FlowPath.Node.builder().seqId(1).switchId(SWITCH_ID_1).portNo(11).build())).build()).build();

    private static final PathPair PATH_1_TO_3_VIA_2 = PathPair.builder()
            .forward(FlowPath.builder().latency(2).nodes(asList(
                    FlowPath.Node.builder().seqId(0).switchId(SWITCH_ID_1).portNo(11).segmentLatency(1L).build(),
                    FlowPath.Node.builder().seqId(1).switchId(SWITCH_ID_2).portNo(11).build(),
                    FlowPath.Node.builder().seqId(2).switchId(SWITCH_ID_2).portNo(12).segmentLatency(1L).build(),
                    FlowPath.Node.builder().seqId(3).switchId(SWITCH_ID_3).portNo(11).build()))
                    .build())
            .reverse(FlowPath.builder().latency(2).nodes(asList(
                    FlowPath.Node.builder().seqId(0).switchId(SWITCH_ID_3).portNo(11).segmentLatency(1L).build(),
                    FlowPath.Node.builder().seqId(1).switchId(SWITCH_ID_2).portNo(12).build(),
                    FlowPath.Node.builder().seqId(2).switchId(SWITCH_ID_2).portNo(11).segmentLatency(1L).build(),
                    FlowPath.Node.builder().seqId(3).switchId(SWITCH_ID_1).portNo(11).build()))
                    .build()).build();

    @Before
    public void setUp() {
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        Switch switch1 = new Switch();
        switch1.setSwitchId(SWITCH_ID_1);
        switchRepository.createOrUpdate(switch1);

        Switch switch2 = new Switch();
        switch2.setSwitchId(SWITCH_ID_2);
        switchRepository.createOrUpdate(switch2);

        Switch switch3 = new Switch();
        switch3.setSwitchId(SWITCH_ID_3);
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
            FlowNotFoundException, FlowAlreadyExistException, FlowValidationException, SwitchValidationException {
        PathComputer pathComputer = mock(PathComputer.class);
        PathComputerFactory pathComputerFactory = mock(PathComputerFactory.class);
        FlowValidator flowValidator = new FlowValidator(persistenceManager.getRepositoryFactory());
        when(pathComputerFactory.getPathComputer()).thenReturn(pathComputer);

        FlowService flowService = new FlowService(persistenceManager,
                pathComputerFactory, new FlowResourcesManager(new ResourceCache()), flowValidator);

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(getOrCreateSwitch(SWITCH_ID_1))
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(getOrCreateSwitch(SWITCH_ID_3))
                .destPort(2)
                .destVlan(102)
                .bandwidth(0)
                .status(FlowStatus.IN_PROGRESS)
                .build();
        when(pathComputer.getPath(any())).thenReturn(PATH_DIRECT_1_TO_3);

        flowService.createFlow(flow, null, mock(FlowCommandSender.class));
        flowService.updateFlowStatus(flow.getFlowId(), FlowStatus.UP);

        when(pathComputer.getPath(any(), eq(true))).thenReturn(PATH_1_TO_3_VIA_2);

        ReroutedFlow reroutedFlow = flowService.rerouteFlow(flow.getFlowId(), true, mock(FlowCommandSender.class));
        assertNotNull(reroutedFlow);
        assertEquals(PATH_1_TO_3_VIA_2.getForward(), reroutedFlow.getNewFlow().getForward().getFlowPath());

        Flow foundFlow = persistenceManager.getRepositoryFactory().createFlowRepository()
                .findById(flow.getFlowId()).iterator().next();
        assertEquals(flow.getFlowId(), foundFlow.getFlowId());
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
