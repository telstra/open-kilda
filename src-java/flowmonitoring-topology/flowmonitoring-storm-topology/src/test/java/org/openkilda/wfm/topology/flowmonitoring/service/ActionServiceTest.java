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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.openkilda.server42.messaging.FlowDirection.FORWARD;
import static org.openkilda.server42.messaging.FlowDirection.REVERSE;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.HEALTHY;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.TIER_1_FAILED;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.TIER_2_FAILED;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.UNSTABLE;

import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.messaging.info.haflow.UpdateHaSubFlowCommand;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.dummy.FlowDefaults;
import org.openkilda.persistence.dummy.PersistenceDummyEntityFactory;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.topology.flowmonitoring.bolt.FlowOperationsCarrier;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class ActionServiceTest extends InMemoryGraphBasedTest {

    private static final Duration NANOSECOND = Duration.ofNanos(1);

    private static final SwitchId SRC_SWITCH = new SwitchId(1);
    private static final SwitchId DST_SWITCH = new SwitchId(2);
    private static final int IN_PORT = 7;
    private static final int OUT_PORT = 8;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final float THRESHOLD = 0.1f;
    public static final int SHARD_COUNT = 1;

    private PersistenceDummyEntityFactory dummyFactory;
    private FlowRepository flowRepository;
    private KildaFeatureTogglesRepository featureTogglesRepository;
    private ActionService service;
    private Flow flow;
    private HaSubFlow haSubFlow;

    @Mock
    private FlowOperationsCarrier carrier;
    private ManualClock clock = new ManualClock();

    @Before
    public void setup() {
        FlowDefaults flowDefaults = new FlowDefaults();
        flowDefaults.setPathComputationStrategy(PathComputationStrategy.LATENCY);
        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager, flowDefaults);

        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        featureTogglesRepository.add(KildaFeatureToggles.builder().flowLatencyMonitoringReactions(true).build());

        createTestSwitch(SRC_SWITCH);
        createTestSwitch(DST_SWITCH);

        flow = dummyFactory.makeFlow(new FlowEndpoint(SRC_SWITCH, IN_PORT),
                new FlowEndpoint(DST_SWITCH, OUT_PORT));

        haSubFlow = createHaSubFlow();

        service = new ActionService(carrier, persistenceManager, clock, TIMEOUT, THRESHOLD, SHARD_COUNT);
    }

    @Test
    public void stayInHealthyState() {
        Duration latency = Duration.ofNanos(flow.getMaxLatency() - 10);

        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, latency);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, latency.minus(NANOSECOND));

        latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) - 1);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> HEALTHY.equals(fsm.getCurrentState())));

        verify(carrier, times(0)).sendFlowRerouteRequest(any());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void stayInHealthyStateHaFlow() {
        Duration latency = Duration.ofNanos(MAX_LATENCY_1 - 10);

        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), FORWARD, latency);
        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), REVERSE, latency.minus(NANOSECOND));

        latency = Duration.ofNanos((long) (MAX_LATENCY_1 * (1 + THRESHOLD)) - 1);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> HEALTHY.equals(fsm.getCurrentState())));

        verify(carrier, times(0)).sendHaFlowRerouteRequest(any());
        verify(carrier, times(0)).sendHaFlowSyncRequest(any());
    }

    @Test
    public void failTier1AndSendRerouteRequest() {
        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_1_FAILED.equals(fsm.getCurrentState())));

        verify(carrier, times(2)).sendFlowRerouteRequest(flow.getFlowId());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void failTier1AndSendRerouteRequestHaFlow() {
        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (MAX_LATENCY_1 * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_1_FAILED.equals(fsm.getCurrentState())));

        verify(carrier, times(2)).sendHaFlowRerouteRequest(haSubFlow.getHaFlowId());
        verify(carrier, times(0)).sendHaFlowSyncRequest(any());
    }

    @Test
    public void failTier1AndDoNotSendRerouteRequestWhenToggleIsFalse() {
        transactionManager.doInTransaction(() -> {
            KildaFeatureToggles featureToggles = featureTogglesRepository.find()
                    .orElseThrow(() -> new IllegalStateException("Feature toggle not found"));
            featureToggles.setFlowLatencyMonitoringReactions(false);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_1_FAILED.equals(fsm.getCurrentState())));

        verify(carrier, times(0)).sendFlowRerouteRequest(any());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void failTier1AndDoNotSendRerouteRequestForCostStrategy() {
        transactionManager.doInTransaction(() -> {
            Flow flowSetup = flowRepository.findById(flow.getFlowId())
                    .orElseThrow(() -> new IllegalStateException("Flow not found"));
            flowSetup.setPathComputationStrategy(PathComputationStrategy.COST);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_1_FAILED.equals(fsm.getCurrentState())));

        verify(carrier, times(0)).sendFlowRerouteRequest(any());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void failTier2AndSendRerouteRequest() {
        transactionManager.doInTransaction(() -> {
            Flow flowSetup = flowRepository.findById(flow.getFlowId())
                    .orElseThrow(() -> new IllegalStateException("Flow not found"));
            flowSetup.setPathComputationStrategy(PathComputationStrategy.MAX_LATENCY);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatencyTier2() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_2_FAILED.equals(fsm.getCurrentState())));

        verify(carrier, times(2)).sendFlowRerouteRequest(flow.getFlowId());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void failTier2AndDoNotSendRerouteRequestForCostStrategy() {
        transactionManager.doInTransaction(() -> {
            Flow flowSetup = flowRepository.findById(flow.getFlowId())
                    .orElseThrow(() -> new IllegalStateException("Flow not found"));
            flowSetup.setPathComputationStrategy(PathComputationStrategy.COST);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatencyTier2() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_2_FAILED.equals(fsm.getCurrentState())));

        verify(carrier, times(0)).sendFlowRerouteRequest(any());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void becomeHealthyAndSendSyncRequest() {
        Duration tier2Failed = Duration.ofNanos(flow.getMaxLatencyTier2() * 2);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, tier2Failed);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, tier2Failed);

        Duration healthy = Duration.ofNanos((long) (flow.getMaxLatency() * (1 - THRESHOLD)) - 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, healthy);
            service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, healthy.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> HEALTHY.equals(fsm.getCurrentState())));

        verify(carrier, times(0)).sendFlowRerouteRequest(any());
        verify(carrier, times(2)).sendFlowSyncRequest(flow.getFlowId());
    }

    @Test
    public void becomeHealthyAndSendSyncRequestHaFlow() {
        Duration tier2Failed = Duration.ofNanos(MAX_LATENCY_TIER_2_1 * 2);
        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), FORWARD, tier2Failed);
        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), REVERSE, tier2Failed);

        Duration healthy = Duration.ofNanos((long) (MAX_LATENCY_1 * (1 - THRESHOLD)) - 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), FORWARD, healthy);
            service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), REVERSE, healthy.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> HEALTHY.equals(fsm.getCurrentState())));

        verify(carrier, times(0)).sendHaFlowRerouteRequest(any());
        verify(carrier, times(2)).sendHaFlowSyncRequest(haSubFlow.getHaFlowId());
    }

    @Test
    public void updateFlowInfo() {
        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        FlowPathDto path = FlowPathDto.builder()
                .forwardPath(Arrays.asList(new PathNodePayload(SRC_SWITCH, 1, 2),
                        new PathNodePayload(DST_SWITCH, 3, 4)))
                .reversePath(Arrays.asList(new PathNodePayload(DST_SWITCH, 4, 3),
                        new PathNodePayload(SRC_SWITCH, 2, 1)))
                .build();
        long maxLatency = flow.getMaxLatency() / 2;
        long maxLatencyTier2 = flow.getMaxLatencyTier2() / 2;
        UpdateFlowCommand info = new UpdateFlowCommand(flow.getFlowId(), path, maxLatency, maxLatencyTier2);
        service.updateFlowInfo(info);

        assertEquals(2, service.fsms.values().size());
        FlowLatencyMonitoringFsm fsm = service.fsms.values().stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Fsm not found"));
        assertEquals(maxLatency, fsm.getMaxLatency());
        assertEquals(maxLatencyTier2, fsm.getMaxLatencyTier2());

        verify(carrier, times(0)).sendFlowRerouteRequest(any());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void updateHaFlowInfo() {
        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(haSubFlow.getHaSubFlowId(), REVERSE, NANOSECOND);

        long maxLatency = MAX_LATENCY_2;
        long maxLatencyTier2 = MAX_LATENCY_TIER_2_2;
        UpdateHaSubFlowCommand info = new UpdateHaSubFlowCommand(haSubFlow.getHaSubFlowId(), maxLatency,
                maxLatencyTier2);
        service.updateHaSubFlowInfo(info);

        assertEquals(2, service.fsms.values().size());
        FlowLatencyMonitoringFsm fsm = service.fsms.values().stream()
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Fsm not found"));
        assertEquals(maxLatency, fsm.getMaxLatency());
        assertEquals(maxLatencyTier2, fsm.getMaxLatencyTier2());

        verify(carrier, times(0)).sendHaFlowRerouteRequest(any());
        verify(carrier, times(0)).sendHaFlowSyncRequest(any());
    }

    @Test
    public void removeFlowInfo() {
        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        service.removeFlowInfo(flow.getFlowId());

        assertTrue(service.fsms.values().isEmpty());

        verify(carrier, times(0)).sendFlowRerouteRequest(any());
        verify(carrier, times(0)).sendFlowSyncRequest(any());
    }

    @Test
    public void removeHaFlowInfo() {
        service.processFlowLatencyMeasurement(flow.getFlowId(), FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), REVERSE, NANOSECOND);

        service.removeFlowInfo(flow.getFlowId());

        assertTrue(service.fsms.values().isEmpty());

        verify(carrier, times(0)).sendHaFlowRerouteRequest(any());
        verify(carrier, times(0)).sendHaFlowSyncRequest(any());
    }

    @Test
    public void needToCheckSlaTest() {
        int shardCount = 4;
        ActionService testService = new ActionService(
                carrier, persistenceManager, clock, TIMEOUT, THRESHOLD, shardCount);
        int[] shardChecks = new int[shardCount];
        for (int hash = -20; hash < 20; hash++) {
            for (int shard = 0; shard < shardCount; shard++) {
                if (testService.needToCheckSla(hash, shard)) {
                    shardChecks[shard]++;
                }
            }
        }

        assertEquals(10, shardChecks[0]);
        assertEquals(10, shardChecks[1]);
        assertEquals(10, shardChecks[2]);
        assertEquals(10, shardChecks[3]);
    }

    private HaSubFlow createHaSubFlow() {
        Switch sharedSwitch = createTestSwitch(SWITCH_ID_3.toLong());
        Switch endpointSwitch = createTestSwitch(SWITCH_ID_4.toLong());

        HaFlow haFlow = dummyFactory.makeHaFlow(HA_FLOW_ID_1, sharedSwitch, PORT_1, MAX_LATENCY_1,
                MAX_LATENCY_TIER_2_1);
        HaSubFlow haSubFlow = dummyFactory.makeHaSubFlow(SUB_FLOW_ID_1, endpointSwitch, PORT_1, VLAN_1, INNER_VLAN_1,
                DESCRIPTION_1);
        haFlow.setHaSubFlows(Sets.newHashSet(haSubFlow));
        return haSubFlow;
    }
}
