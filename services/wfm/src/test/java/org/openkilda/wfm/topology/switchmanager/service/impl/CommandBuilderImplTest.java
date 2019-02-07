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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class CommandBuilderImplTest {

    private static final SwitchId SWITCH_ID_A = new SwitchId("00:10");
    private static final SwitchId SWITCH_ID_B = new SwitchId("00:20");
    private static final SwitchId SWITCH_ID_C = new SwitchId("00:30");

    @Test
    public void testCommandBuilder() {
        CommandBuilder commandBuilder = new CommandBuilderImpl(persistenceManager().build());
        List<BaseInstallFlow> response = commandBuilder
                .buildCommandsToSyncRules(SWITCH_ID_B, Arrays.asList(1L, 2L, 3L, 4L));
        assertEquals(4, response.size());
        assertTrue(response.get(0) instanceof InstallTransitFlow);
        assertTrue(response.get(1) instanceof InstallEgressFlow);
        assertTrue(response.get(2) instanceof InstallOneSwitchFlow);
        assertTrue(response.get(3) instanceof InstallIngressFlow);
    }

    private PersistenceManager persistenceManager() {
        return new PersistenceManager();
    }

    private static class PersistenceManager {

        private FlowSegment flowSegmentA = FlowSegment.builder()
                .flowId("A")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_B).build())
                .cookie(1L)
                .build();

        private FlowSegment flowSegmentB = FlowSegment.builder()
                .flowId("B")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_B).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_C).build())
                .cookie(2L)
                .build();

        private FlowSegment flowSegmentC = FlowSegment.builder()
                .flowId("C")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_C).build())
                .cookie(4L)
                .build();

        private Flow flowA = Flow.builder()
                .flowId("A")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_C).build())
                .cookie(1L)
                .transitVlan(1)
                .build();

        private Flow flowB = Flow.builder()
                .flowId("B")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_C).build())
                .cookie(2L)
                .transitVlan(1)
                .build();

        private Flow flowC = Flow.builder()
                .flowId("C")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .cookie(3L)
                .transitVlan(1)
                .build();

        private Flow flowD = Flow.builder()
                .flowId("D")
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_C).build())
                .cookie(4L)
                .transitVlan(1)
                .build();

        private org.openkilda.persistence.PersistenceManager build() {

            FlowSegmentRepository flowSegmentRepository = mock(FlowSegmentRepository.class);
            when(flowSegmentRepository.findByDestSwitchId(SWITCH_ID_B))
                    .thenReturn(Arrays.asList(flowSegmentA, flowSegmentB));
            when(flowSegmentRepository.findBySrcSwitchIdAndCookie(SWITCH_ID_A, 2L))
                    .thenReturn(Optional.of(flowSegmentB));
            when(flowSegmentRepository.findBySrcSwitchIdAndCookie(SWITCH_ID_B, 1L))
                    .thenReturn(Optional.of(flowSegmentB));
            when(flowSegmentRepository.findBySrcSwitchIdAndCookie(SWITCH_ID_A, 4L))
                    .thenReturn(Optional.of(flowSegmentC));
            FlowRepository flowRepository = mock(FlowRepository.class);
            when(flowRepository.findBySrcSwitchId(any())).thenReturn(Arrays.asList(flowC, flowD));
            when(flowRepository.findByIdAndCookie("A", 1L)).thenReturn(Optional.of(flowA));
            when(flowRepository.findByIdAndCookie("B", 2L)).thenReturn(Optional.of(flowB));
            RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
            when(repositoryFactory.createFlowSegmentRepository()).thenReturn(flowSegmentRepository);
            when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
            org.openkilda.persistence.PersistenceManager persistenceManager =
                    mock(org.openkilda.persistence.PersistenceManager.class);
            when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
            return persistenceManager;
        }
    }
}
