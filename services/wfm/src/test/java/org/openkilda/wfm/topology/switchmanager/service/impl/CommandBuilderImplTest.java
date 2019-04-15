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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CommandBuilderImplTest {

    private static final SwitchId SWITCH_ID_A = new SwitchId("00:10");
    private static final SwitchId SWITCH_ID_B = new SwitchId("00:20");
    private static final SwitchId SWITCH_ID_C = new SwitchId("00:30");

    @Test
    public void testCommandBuilder() {
        CommandBuilder commandBuilder = new CommandBuilderImpl(persistenceManager().build());
        List<BaseInstallFlow> response = commandBuilder
                .buildCommandsToSyncRules(SWITCH_ID_B, asList(1L, 2L, 3L, 4L));
        assertEquals(4, response.size());
        assertTrue(response.get(0) instanceof InstallEgressFlow);
        assertTrue(response.get(1) instanceof InstallTransitFlow);
        assertTrue(response.get(2) instanceof InstallOneSwitchFlow);
        assertTrue(response.get(3) instanceof InstallIngressFlow);
    }

    private PersistenceManagerBuilder persistenceManager() {
        return new PersistenceManagerBuilder();
    }

    private static class PersistenceManagerBuilder {
        private FlowRepository flowRepository = mock(FlowRepository.class);
        private FlowPathRepository flowPathRepository = mock(FlowPathRepository.class);
        private TransitVlanRepository transitVlanRepository = mock(TransitVlanRepository.class);

        private FlowPath buildFlowAndPath(String flowId, SwitchId srcSwitchId, SwitchId destSwitchId,
                                          int cookie, int transitVlan) {
            boolean forward = srcSwitchId.compareTo(destSwitchId) <= 0;
            Switch srcSwitch = Switch.builder().switchId(srcSwitchId).build();
            Switch destSwitch = Switch.builder().switchId(destSwitchId).build();
            FlowPath flowPath = FlowPath.builder()
                    .flowId(flowId)
                    .pathId(new PathId(UUID.randomUUID().toString()))
                    .srcSwitch(srcSwitch)
                    .destSwitch(destSwitch)
                    .cookie(new Cookie(cookie))
                    .segments(emptyList())
                    .build();
            when(flowPathRepository.findById(eq(flowPath.getPathId())))
                    .thenReturn(Optional.of(flowPath));
            when(flowPathRepository.findByFlowId(eq(flowId)))
                    .thenReturn(asList(flowPath));

            Flow flow = Flow.builder()
                    .flowId(flowId)
                    .srcSwitch(forward ? srcSwitch : destSwitch)
                    .destSwitch(forward ? destSwitch : srcSwitch)
                    .forwardPath(forward ? flowPath : null)
                    .reversePath(forward ? null : flowPath)
                    .build();
            when(flowRepository.findById(eq(flowId)))
                    .thenReturn(Optional.of(flow));

            TransitVlan transitVlanEntity = TransitVlan.builder()
                    .flowId(flowPath.getFlowId())
                    .pathId(flowPath.getPathId())
                    .vlan(transitVlan)
                    .build();
            when(transitVlanRepository.findByPathId(eq(flowPath.getPathId())))
                    .thenReturn(singleton(transitVlanEntity));

            return flowPath;
        }

        private PathSegment buildSegment(PathId pathId, SwitchId srcSwitchId, SwitchId destSwitchId) {
            return PathSegment.builder()
                    .pathId(pathId)
                    .srcSwitch(Switch.builder().switchId(srcSwitchId).build())
                    .destSwitch(Switch.builder().switchId(destSwitchId).build())
                    .build();
        }

        private PersistenceManager build() {
            FlowPath flowPathA = buildFlowAndPath("A", SWITCH_ID_A, SWITCH_ID_B, 1, 1);
            flowPathA.setSegments(asList(buildSegment(flowPathA.getPathId(), SWITCH_ID_A, SWITCH_ID_C),
                    buildSegment(flowPathA.getPathId(), SWITCH_ID_C, SWITCH_ID_B)));

            FlowPath flowPathB = buildFlowAndPath("B", SWITCH_ID_A, SWITCH_ID_C, 2, 1);
            flowPathB.setSegments(asList(buildSegment(flowPathB.getPathId(), SWITCH_ID_A, SWITCH_ID_B),
                    buildSegment(flowPathB.getPathId(), SWITCH_ID_B, SWITCH_ID_C)));

            FlowPath flowPathC = buildFlowAndPath("C", SWITCH_ID_A, SWITCH_ID_A, 3, 1);

            FlowPath flowPathD = buildFlowAndPath("D", SWITCH_ID_B, SWITCH_ID_A, 4, 1);
            flowPathD.setSegments(asList(buildSegment(flowPathD.getPathId(), SWITCH_ID_B, SWITCH_ID_A)));

            when(flowPathRepository.findBySegmentDestSwitch(eq(SWITCH_ID_B)))
                    .thenReturn(Arrays.asList(flowPathA, flowPathB));
            when(flowPathRepository.findByEndpointSwitch(eq(SWITCH_ID_B)))
                    .thenReturn(Arrays.asList(flowPathC, flowPathD));

            RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
            when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
            when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
            when(repositoryFactory.createTransitVlanRepository()).thenReturn(transitVlanRepository);

            PersistenceManager persistenceManager = mock(PersistenceManager.class);
            when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
            return persistenceManager;
        }
    }
}
