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

package org.openkilda.wfm.topology.flowmonitoring.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.openkilda.model.IslStatus.ACTIVE;

import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.messaging.info.haflow.UpdateHaSubFlowCommand;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Isl;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.dummy.IslDirectionalReference;
import org.openkilda.persistence.dummy.PersistenceDummyEntityFactory;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.share.utils.TimestampHelper;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class FlowCacheServiceTest extends InMemoryGraphBasedTest {
    private static final Duration FLOW_RTT_STATS_EXPIRATION_TIME = Duration.ofSeconds(3);

    private static final int IN_PORT = 7;
    private static final int OUT_PORT = 8;
    private static final int ISL_SRC_PORT = 10;
    private static final int ISL_DST_PORT = 20;
    private static final int ISL_SRC_PORT_2 = 11;
    private static final int ISL_DST_PORT_2 = 21;
    private static final FlowSegmentCookie COOKIE_1 = FlowSegmentCookie.builder()
            .flowEffectiveId(1).direction(FlowPathDirection.FORWARD).build();
    private static final FlowSegmentCookie COOKIE_2 = FlowSegmentCookie.builder()
            .flowEffectiveId(1).direction(FlowPathDirection.REVERSE).build();

    private PersistenceDummyEntityFactory dummyFactory;
    private IslRepository islRepository;
    private FlowCacheService service;

    private Switch firstSwitch;
    private Switch secondSwitch;
    private Switch thirdSwitch;

    @Mock
    private FlowCacheBoltCarrier carrier;
    @Mock
    private Clock clock;

    @BeforeEach
    public void setup() {
        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager);

        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        firstSwitch = createTestSwitch(SWITCH_ID_1.toLong());
        secondSwitch = createTestSwitch(SWITCH_ID_2.toLong());
        thirdSwitch = createTestSwitch(SWITCH_ID_3.toLong());

        createIsl(firstSwitch, ISL_SRC_PORT, secondSwitch, ISL_DST_PORT);
        createIsl(secondSwitch, ISL_DST_PORT, firstSwitch, ISL_SRC_PORT);
        createIsl(firstSwitch, ISL_SRC_PORT_2, secondSwitch, ISL_DST_PORT_2);
        createIsl(secondSwitch, ISL_DST_PORT_2, firstSwitch, ISL_SRC_PORT_2);
    }

    @Test
    public void sendCheckLatencyRequests() {
        when(clock.instant()).thenReturn(Instant.now());
        dummyFactory.makeFlow(new FlowEndpoint(SWITCH_ID_1, 8), new FlowEndpoint(SWITCH_ID_1, 9));
        Flow flow = createFlow();
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
        service.activate();

        service.processFlowLatencyCheck(flow.getFlowId());

        List<Link> expectedForwardPath = getLinks(SWITCH_ID_1, ISL_SRC_PORT, SWITCH_ID_2, ISL_DST_PORT);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.FORWARD, expectedForwardPath,
                null);
        List<Link> expectedReversePath = reverse(expectedForwardPath);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.REVERSE, expectedReversePath,
                null);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void removeFlowFromCache() {
        Flow flow = createFlow();
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
        service.activate();

        service.removeFlowInfo(flow.getFlowId());

        service.processFlowLatencyCheck(flow.getFlowId());

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void changeFlowPathInCache() {
        when(clock.instant()).thenReturn(Instant.now());
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
        service.activate();

        Long maxLatency = 100L;
        Long maxLatencyTier2 = 200L;

        Flow flow = createFlow();
        UpdateFlowCommand updateFlowCommand = new UpdateFlowCommand(flow.getFlowId(), FlowPathDto.builder()
                .id(flow.getFlowId())
                .forwardPath(Arrays.asList(new PathNodePayload(SWITCH_ID_1, IN_PORT, ISL_SRC_PORT_2),
                        new PathNodePayload(SWITCH_ID_2, ISL_DST_PORT_2, OUT_PORT)))
                .reversePath(Arrays.asList(new PathNodePayload(SWITCH_ID_2, OUT_PORT, ISL_DST_PORT_2),
                        new PathNodePayload(SWITCH_ID_1, ISL_SRC_PORT_2, IN_PORT)))
                .build(), maxLatency, maxLatencyTier2);

        service.updateFlowInfo(updateFlowCommand);
        service.processFlowLatencyCheck(flow.getFlowId());

        List<Link> expectedForwardPath = getLinks(SWITCH_ID_1, ISL_SRC_PORT_2, SWITCH_ID_2, ISL_DST_PORT_2);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.FORWARD, expectedForwardPath,
                null);
        List<Link> expectedReversePath = reverse(expectedForwardPath);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.REVERSE, expectedReversePath,
                null);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void sendCheckFlowSlaRequests() {
        Instant now = Instant.now();
        when(clock.instant()).thenReturn(now);

        Flow flow = createFlow();
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
        service.activate();

        long t0 = (now.getEpochSecond() << 32) + 1234;
        long t1 = (now.getEpochSecond() << 32) + 2345;
        FlowRttStatsData flowRttStatsData = FlowRttStatsData.builder()
                .flowId(flow.getFlowId())
                .direction(FlowDirection.FORWARD.name().toLowerCase())
                .t0(t0)
                .t1(t1)
                .build();
        service.processFlowRttStatsData(flowRttStatsData);
        service.processFlowLatencyCheck(flow.getFlowId());

        List<Link> expectedForwardPath = getLinks(SWITCH_ID_1, ISL_SRC_PORT, SWITCH_ID_2, ISL_DST_PORT);
        verify(carrier).emitCheckFlowLatencyRequest(flow.getFlowId(), FlowDirection.FORWARD,
                TimestampHelper.noviflowTimestampsToDuration(t0, t1));
        List<Link> expectedReversePath = reverse(expectedForwardPath);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.REVERSE, expectedReversePath,
                null);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void activationDeactivationAndReactivation() {
        createFlow();
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
        service.activate();
        //check service.flowStates is not empty
        assertFalse(service.flowStatesIsEmpty());
        // deactivate service
        service.deactivate();
        //check service.flowStates is empty
        assertTrue(service.flowStatesIsEmpty());
        // reactivate service
        service.activate();
        //check service.flowStates is not empty
        assertFalse(service.flowStatesIsEmpty());
    }

    @Test
    public void changeHaFlowPathInCache() {
        when(clock.instant()).thenReturn(Instant.now());
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
        service.activate();

        HaFlow haFlow = dummyFactory.makeHaFlow(HA_FLOW_ID_1, thirdSwitch, PORT_1, 50L, 100L);
        HaSubFlow firstHaSubFlow = dummyFactory.makeHaSubFlow(SUB_FLOW_ID_1, firstSwitch, PORT_1, VLAN_1, INNER_VLAN_1,
                DESCRIPTION_1);
        HaSubFlow secondHaSubFlow = dummyFactory.makeHaSubFlow(SUB_FLOW_ID_2, secondSwitch, PORT_2, VLAN_2,
                INNER_VLAN_2, DESCRIPTION_2);
        HashSet<HaSubFlow> haSubFlows = Sets.newHashSet(firstHaSubFlow, secondHaSubFlow);
        haFlow.setHaSubFlows(haSubFlows);

        PathId forwardPathId = new PathId(HA_FLOW_ID_1 + "_ha_path_1");
        HaFlowPath forwardHaFlowPath = dummyFactory.makeHaFlowPath(forwardPathId, 0, COOKIE_1,
                METER_ID_1, METER_ID_2, thirdSwitch, firstSwitch.getSwitchId(), GROUP_ID_1);
        forwardHaFlowPath.setHaSubFlows(haSubFlows);
        FlowPath forwardSubPath = dummyFactory.makeFlowPath(
                forwardHaFlowPath.getHaPathId().append("_sub_path"), forwardHaFlowPath, thirdSwitch, firstSwitch);
        forwardSubPath.setHaSubFlow(firstHaSubFlow);
        forwardSubPath.setSegments(Lists.newArrayList(dummyFactory.makePathSegment(forwardPathId, thirdSwitch,
                firstSwitch)));
        forwardHaFlowPath.setSubPaths(Lists.newArrayList(forwardSubPath));

        PathId reversePathId = new PathId(HA_FLOW_ID_1 + "_ha_path_2");
        HaFlowPath reverseHaFlowPath = dummyFactory.makeHaFlowPath(reversePathId, 0, COOKIE_2,
                METER_ID_1, METER_ID_2, thirdSwitch, firstSwitch.getSwitchId(), GROUP_ID_1);
        reverseHaFlowPath.setHaSubFlows(haSubFlows);
        FlowPath reverseSubPath = dummyFactory.makeFlowPath(
                reverseHaFlowPath.getHaPathId().append("_sub_path"), reverseHaFlowPath, firstSwitch, thirdSwitch);
        reverseSubPath.setHaSubFlow(firstHaSubFlow);
        reverseSubPath.setSegments(Lists.newArrayList(dummyFactory.makePathSegment(reversePathId, firstSwitch,
                thirdSwitch)));
        reverseHaFlowPath.setSubPaths(Lists.newArrayList(reverseSubPath));

        haFlow.setForwardPath(forwardHaFlowPath);
        haFlow.setReversePath(reverseHaFlowPath);

        Long maxLatency = 100L;
        Long maxLatencyTier2 = 200L;

        UpdateHaSubFlowCommand updateHaSubFlowCommand = new UpdateHaSubFlowCommand(firstHaSubFlow.getHaSubFlowId(),
                maxLatency, maxLatencyTier2);

        service.updateHaFlowInfo(updateHaSubFlowCommand);
        service.processFlowLatencyCheck(firstHaSubFlow.getHaSubFlowId());

        List<Link> expectedForwardPath = getLinks(SWITCH_ID_3, PORT_0, SWITCH_ID_1, PORT_0);
        verify(carrier).emitCalculateFlowLatencyRequest(firstHaSubFlow.getHaSubFlowId(), FlowDirection.FORWARD,
                expectedForwardPath, haFlow.getHaFlowId());
        List<Link> expectedReversePath = reverse(expectedForwardPath);
        verify(carrier).emitCalculateFlowLatencyRequest(firstHaSubFlow.getHaSubFlowId(), FlowDirection.REVERSE,
                expectedReversePath, haFlow.getHaFlowId());

        verifyNoMoreInteractions(carrier);
    }

    private void createIsl(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort) {
        Isl isl = Isl.builder()
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .status(ACTIVE)
                .latency(100)
                .build();
        islRepository.add(isl);
    }

    private Flow createFlow() {
        FlowEndpoint flowSource = new FlowEndpoint(SWITCH_ID_1, IN_PORT);
        FlowEndpoint flowDestination = new FlowEndpoint(SWITCH_ID_2, OUT_PORT);
        IslDirectionalReference islDirectionalReference = new IslDirectionalReference(
                new IslEndpoint(SWITCH_ID_1, ISL_SRC_PORT),
                new IslEndpoint(SWITCH_ID_2, ISL_DST_PORT));
        return dummyFactory.makeFlow(flowSource, flowDestination, islDirectionalReference);
    }

    private List<Link> getLinks(SwitchId srcSwitch, int srcPort, SwitchId dstSwitchId, int dstPort) {
        return Collections.singletonList(Link.builder()
                .srcSwitchId(srcSwitch)
                .srcPort(srcPort)
                .destSwitchId(dstSwitchId)
                .destPort(dstPort)
                .build());
    }

    private List<Link> reverse(List<Link> links) {
        return links.stream()
                .map(link -> Link.builder()
                        .srcSwitchId(link.getDestSwitchId())
                        .srcPort(link.getDestPort())
                        .destSwitchId(link.getSrcSwitchId())
                        .destPort(link.getSrcPort())
                        .build())
                .collect(Collectors.toList());
    }
}
