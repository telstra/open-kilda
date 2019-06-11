/* Copyright 2019 Telstra Open Source
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.reroute.bolts.MessageSender;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RerouteServiceTest {

    private static final SwitchId SWITCH_ID_A = new SwitchId(1L);
    private static final Switch SWITCH_A = Switch.builder().switchId(SWITCH_ID_A).build();
    private static final SwitchId SWITCH_ID_B = new SwitchId(2L);
    private static final Switch SWITCH_B = Switch.builder().switchId(SWITCH_ID_B).build();
    private static final SwitchId SWITCH_ID_C = new SwitchId(3L);
    private static final Switch SWITCH_C = Switch.builder().switchId(SWITCH_ID_C).build();
    private static final int PORT = 1;

    private static final PathNode PATH_NODE = new PathNode(SWITCH_ID_A, PORT, 1);
    private static final String REASON = "REASON";
    private static final RerouteAffectedFlows REROUTE_AFFECTED_FLOWS_COMMAND = new RerouteAffectedFlows(PATH_NODE,
            REASON);

    private static final RerouteInactiveFlows REROUTE_INACTIVE_FLOWS_COMMAND = new RerouteInactiveFlows(PATH_NODE,
            REASON);

    private static final String FLOW_ID = "TEST_FLOW";
    private static final String CORRELATION_ID = "CORRELATION_ID";

    private Flow pinnedFlow;
    private Flow unpinnedFlow;

    @Before
    public void setup() {
        pinnedFlow = Flow.builder().flowId(FLOW_ID).srcSwitch(SWITCH_A)
                .destSwitch(SWITCH_C).pinned(true).build();
        FlowPath pinnedFlowForwardPath = FlowPath.builder().pathId(new PathId("1"))
                .flow(pinnedFlow).srcSwitch(SWITCH_A).destSwitch(SWITCH_C).cookie(Cookie.buildForwardCookie(1)).build();
        List<PathSegment> pinnedFlowForwardSegments = new ArrayList<>();
        pinnedFlowForwardSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_A)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(1)
                .build());
        pinnedFlowForwardSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_B)
                .srcPort(2)
                .destSwitch(SWITCH_C)
                .destPort(1)
                .build());
        pinnedFlowForwardPath.setSegments(pinnedFlowForwardSegments);

        FlowPath pinnedFlowReversePath = FlowPath.builder().pathId(new PathId("2"))
                .flow(pinnedFlow).srcSwitch(SWITCH_C).destSwitch(SWITCH_A).cookie(Cookie.buildReverseCookie(2)).build();
        List<PathSegment> pinnedFlowReverseSegments = new ArrayList<>();
        pinnedFlowReverseSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_C)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(2)
                .build());
        pinnedFlowReverseSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_B)
                .srcPort(1)
                .destSwitch(SWITCH_A)
                .destPort(1)
                .build());
        pinnedFlowReversePath.setSegments(pinnedFlowReverseSegments);
        pinnedFlow.setForwardPath(pinnedFlowForwardPath);
        pinnedFlow.setReversePath(pinnedFlowReversePath);

        unpinnedFlow = Flow.builder().flowId(FLOW_ID).srcSwitch(SWITCH_A)
                .destSwitch(SWITCH_C).pinned(false).build();
        FlowPath unpinnedFlowForwardPath = FlowPath.builder().pathId(new PathId("3"))
                .flow(unpinnedFlow).srcSwitch(SWITCH_A).destSwitch(SWITCH_C).cookie(Cookie.buildForwardCookie(3))
                .build();
        List<PathSegment> unpinnedFlowForwardSegments = new ArrayList<>();
        unpinnedFlowForwardSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_A)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(1)
                .build());
        unpinnedFlowForwardSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_B)
                .srcPort(2)
                .destSwitch(SWITCH_C)
                .destPort(1)
                .build());
        unpinnedFlowForwardPath.setSegments(unpinnedFlowForwardSegments);

        FlowPath unpinnedFlowReversePath = FlowPath.builder().pathId(new PathId("4"))
                .flow(unpinnedFlow).srcSwitch(SWITCH_C).destSwitch(SWITCH_A).cookie(Cookie.buildReverseCookie(3))
                .build();
        List<PathSegment> unpinnedFlowReverseSegments = new ArrayList<>();
        unpinnedFlowReverseSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_C)
                .srcPort(1)
                .destSwitch(SWITCH_B)
                .destPort(2)
                .build());
        unpinnedFlowReverseSegments.add(PathSegment.builder()
                .srcSwitch(SWITCH_B)
                .srcPort(1)
                .destSwitch(SWITCH_A)
                .destPort(1)
                .build());
        unpinnedFlowReversePath.setSegments(unpinnedFlowReverseSegments);
        unpinnedFlow.setForwardPath(unpinnedFlowForwardPath);
        unpinnedFlow.setReversePath(unpinnedFlowReversePath);
    }


    @Test
    public void testRerouteInactivePinnedFlowsOneFailedSegment() {
        pinnedFlow.setStatus(FlowStatus.DOWN);
        for (FlowPath flowPath : pinnedFlow.getPaths()) {
            flowPath.setStatus(FlowPathStatus.INACTIVE);
            for (PathSegment pathSegment : flowPath.getSegments()) {
                if (pathSegment.containsNode(SWITCH_ID_A, PORT)) {
                    pathSegment.setFailed(true);
                }
            }
        }
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        FlowRepository flowRepository = mock(FlowRepository.class);
        when(flowRepository.findDownFlows())
                .thenReturn(Collections.singletonList(pinnedFlow));
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        MessageSender messageSender = mock(MessageSender.class);
        RerouteService rerouteService = new RerouteService(repositoryFactory);
        rerouteService.rerouteInactiveFlows(messageSender, CORRELATION_ID,
                REROUTE_INACTIVE_FLOWS_COMMAND);
        verify(flowRepository).createOrUpdate(pinnedFlow);
        assertTrue(FlowStatus.UP.equals(pinnedFlow.getStatus()));
        for (FlowPath fp : pinnedFlow.getPaths()) {
            assertTrue(FlowPathStatus.ACTIVE.equals(fp.getStatus()));
            for (PathSegment ps : fp.getSegments()) {
                if (ps.containsNode(SWITCH_ID_A, PORT)) {
                    assertFalse(ps.isFailed());
                }
            }
        }
    }

    @Test
    public void testRerouteInactivePinnedFlowsTwoFailedSegments() {
        pinnedFlow.setStatus(FlowStatus.DOWN);
        for (FlowPath flowPath : pinnedFlow.getPaths()) {
            flowPath.setStatus(FlowPathStatus.INACTIVE);
            for (PathSegment pathSegment : flowPath.getSegments()) {
                pathSegment.setFailed(true);
            }
        }
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        FlowRepository flowRepository = mock(FlowRepository.class);
        when(flowRepository.findDownFlows())
                .thenReturn(Collections.singletonList(pinnedFlow));
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        MessageSender messageSender = mock(MessageSender.class);
        RerouteService rerouteService = new RerouteService(repositoryFactory);
        rerouteService.rerouteInactiveFlows(messageSender, CORRELATION_ID,
                REROUTE_INACTIVE_FLOWS_COMMAND);
        verify(flowRepository).createOrUpdate(pinnedFlow);
        assertTrue(FlowStatus.DOWN.equals(pinnedFlow.getStatus()));
        for (FlowPath fp : pinnedFlow.getPaths()) {
            assertTrue(FlowPathStatus.INACTIVE.equals(fp.getStatus()));
            for (PathSegment ps : fp.getSegments()) {
                if (ps.containsNode(SWITCH_ID_A, PORT)) {
                    assertFalse(ps.isFailed());
                } else {
                    assertTrue(ps.isFailed());
                }
            }
        }

    }
}
