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

package org.openkilda.wfm.topology.flowmonitoring.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.openkilda.model.IslStatus.ACTIVE;

import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Isl;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.dummy.IslDirectionalReference;
import org.openkilda.persistence.dummy.PersistenceDummyEntityFactory;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.share.utils.TimestampHelper;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class FlowCacheServiceTest extends InMemoryGraphBasedTest {
    private static final Duration FLOW_RTT_STATS_EXPIRATION_TIME = Duration.ofSeconds(3);

    private static final SwitchId SRC_SWITCH = new SwitchId(1);
    private static final SwitchId DST_SWITCH = new SwitchId(2);
    private static final int IN_PORT = 7;
    private static final int OUT_PORT = 8;
    private static final int ISL_SRC_PORT = 10;
    private static final int ISL_DST_PORT = 20;
    private static final int ISL_SRC_PORT_2 = 11;
    private static final int ISL_DST_PORT_2 = 21;

    private PersistenceDummyEntityFactory dummyFactory;
    private IslRepository islRepository;
    private FlowCacheService service;

    @Mock
    private FlowCacheBoltCarrier carrier;
    @Mock
    private Clock clock;

    @Before
    public void setup() {
        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager);

        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        Switch firstSwitch = createTestSwitch(SRC_SWITCH.toLong());
        Switch secondSwitch = createTestSwitch(DST_SWITCH.toLong());

        createIsl(firstSwitch, ISL_SRC_PORT, secondSwitch, ISL_DST_PORT);
        createIsl(secondSwitch, ISL_DST_PORT, firstSwitch, ISL_SRC_PORT);
        createIsl(firstSwitch, ISL_SRC_PORT_2, secondSwitch, ISL_DST_PORT_2);
        createIsl(secondSwitch, ISL_DST_PORT_2, firstSwitch, ISL_SRC_PORT_2);
    }

    @Test
    public void shouldSendCheckLatencyRequests() {
        when(clock.instant()).thenReturn(Instant.now());
        dummyFactory.makeFlow(new FlowEndpoint(SRC_SWITCH, 8), new FlowEndpoint(SRC_SWITCH, 9));
        Flow flow = createFlow();
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);

        service.processFlowLatencyCheck(flow.getFlowId());

        List<Link> expectedForwardPath = getLinks(SRC_SWITCH, ISL_SRC_PORT, DST_SWITCH, ISL_DST_PORT);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.FORWARD, expectedForwardPath);
        List<Link> expectedReversePath = reverse(expectedForwardPath);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.REVERSE, expectedReversePath);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldRemoveFlowFromCache() {
        Flow flow = createFlow();
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
        service.removeFlowInfo(flow.getFlowId());

        service.processFlowLatencyCheck(flow.getFlowId());

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldChangeFlowPathInCache() {
        Flow flow = createFlow();
        when(clock.instant()).thenReturn(Instant.now());
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);

        Long maxLatency = 100L;
        Long maxLatencyTier2 = 200L;
        UpdateFlowCommand updateFlowCommand = new UpdateFlowCommand(flow.getFlowId(), FlowPathDto.builder()
                .id(flow.getFlowId())
                .forwardPath(Arrays.asList(new PathNodePayload(SRC_SWITCH, IN_PORT, ISL_SRC_PORT_2),
                        new PathNodePayload(DST_SWITCH, ISL_DST_PORT_2, OUT_PORT)))
                .reversePath(Arrays.asList(new PathNodePayload(DST_SWITCH, OUT_PORT, ISL_DST_PORT_2),
                        new PathNodePayload(SRC_SWITCH, ISL_SRC_PORT_2, IN_PORT)))
                .build(), maxLatency, maxLatencyTier2);

        service.updateFlowInfo(updateFlowCommand);
        service.processFlowLatencyCheck(flow.getFlowId());

        List<Link> expectedForwardPath = getLinks(SRC_SWITCH, ISL_SRC_PORT_2, DST_SWITCH, ISL_DST_PORT_2);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.FORWARD, expectedForwardPath);
        List<Link> expectedReversePath = reverse(expectedForwardPath);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.REVERSE, expectedReversePath);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldSendCheckFlowSlaRequests() {
        Flow flow = createFlow();
        Instant now = Instant.now();
        when(clock.instant()).thenReturn(now);
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);

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

        List<Link> expectedForwardPath = getLinks(SRC_SWITCH, ISL_SRC_PORT, DST_SWITCH, ISL_DST_PORT);
        verify(carrier).emitCheckFlowLatencyRequest(flow.getFlowId(), FlowDirection.FORWARD,
                TimestampHelper.noviflowTimestampsToDuration(t0, t1));
        List<Link> expectedReversePath = reverse(expectedForwardPath);
        verify(carrier).emitCalculateFlowLatencyRequest(flow.getFlowId(), FlowDirection.REVERSE, expectedReversePath);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void serviceActivationDeactivationAndReactivation() {
        createFlow();
        service = new FlowCacheService(persistenceManager, clock, FLOW_RTT_STATS_EXPIRATION_TIME, carrier);
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
        FlowEndpoint flowSource = new FlowEndpoint(SRC_SWITCH, IN_PORT);
        FlowEndpoint flowDestination = new FlowEndpoint(DST_SWITCH, OUT_PORT);
        IslDirectionalReference islDirectionalReference = new IslDirectionalReference(
                new IslEndpoint(SRC_SWITCH, ISL_SRC_PORT),
                new IslEndpoint(DST_SWITCH, ISL_DST_PORT));
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
