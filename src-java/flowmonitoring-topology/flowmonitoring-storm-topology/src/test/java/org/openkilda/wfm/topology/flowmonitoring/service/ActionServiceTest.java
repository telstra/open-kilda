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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.HEALTHY;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.TIER_1_FAILED;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.TIER_2_FAILED;
import static org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State.UNSTABLE;

import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStats;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.dummy.FlowDefaults;
import org.openkilda.persistence.dummy.PersistenceDummyEntityFactory;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowStatsRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.topology.flowmonitoring.bolt.FlowOperationsCarrier;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm;

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
    private FlowStatsRepository flowStatsRepository;
    private KildaFeatureTogglesRepository featureTogglesRepository;
    private ActionService service;
    private Flow flow;

    @Mock
    private FlowOperationsCarrier carrier;
    private ManualClock clock = new ManualClock();

    @Before
    public void setup() {
        FlowDefaults flowDefaults = new FlowDefaults();
        flowDefaults.setPathComputationStrategy(PathComputationStrategy.LATENCY);
        dummyFactory = new PersistenceDummyEntityFactory(persistenceManager, flowDefaults);

        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        flowStatsRepository = persistenceManager.getRepositoryFactory().createFlowStatsRepository();
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        featureTogglesRepository.add(KildaFeatureToggles.builder().flowLatencyMonitoringReactions(true).build());

        createTestSwitch(SRC_SWITCH);
        createTestSwitch(DST_SWITCH);

        flow = dummyFactory.makeFlow(new FlowEndpoint(SRC_SWITCH, IN_PORT),
                new FlowEndpoint(DST_SWITCH, OUT_PORT));

        service = new ActionService(carrier, persistenceManager, clock, TIMEOUT, THRESHOLD, SHARD_COUNT);
    }

    @Test
    public void shouldStayInHealthyState() {
        Duration latency = Duration.ofNanos(flow.getMaxLatency() - 10);

        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, latency);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, latency.minus(NANOSECOND));

        latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) - 1);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> HEALTHY.equals(fsm.getCurrentState())));
        FlowStats actual = flowStatsRepository.findByFlowId(flow.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        assertEquals(latency.getNano(), actual.getForwardLatency().intValue());
        assertEquals(latency.minus(NANOSECOND).getNano(), actual.getReverseLatency().intValue());

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldFailTier1AndSendRerouteRequest() {
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_1_FAILED.equals(fsm.getCurrentState())));
        FlowStats actual = flowStatsRepository.findByFlowId(flow.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        assertEquals(latency.getNano(), actual.getForwardLatency().intValue());
        assertEquals(latency.minus(NANOSECOND).getNano(), actual.getReverseLatency().intValue());

        verify(carrier, times(2)).sendFlowRerouteRequest(flow.getFlowId());
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldFailTier1AndDoNotSendRerouteRequestWhenToggleIsFalse() {
        transactionManager.doInTransaction(() -> {
            KildaFeatureToggles featureToggles = featureTogglesRepository.find()
                    .orElseThrow(() -> new IllegalStateException("Feature toggle not found"));
            featureToggles.setFlowLatencyMonitoringReactions(false);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_1_FAILED.equals(fsm.getCurrentState())));
        FlowStats actual = flowStatsRepository.findByFlowId(flow.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        assertEquals(latency.getNano(), actual.getForwardLatency().intValue());
        assertEquals(latency.minus(NANOSECOND).getNano(), actual.getReverseLatency().intValue());

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldFailTier1AndDoNotSendRerouteRequestForCostStrategy() {
        transactionManager.doInTransaction(() -> {
            Flow flowSetup = flowRepository.findById(flow.getFlowId())
                    .orElseThrow(() -> new IllegalStateException("Flow not found"));
            flowSetup.setPathComputationStrategy(PathComputationStrategy.COST);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatency() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_1_FAILED.equals(fsm.getCurrentState())));
        FlowStats actual = flowStatsRepository.findByFlowId(flow.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        assertEquals(latency.getNano(), actual.getForwardLatency().intValue());
        assertEquals(latency.minus(NANOSECOND).getNano(), actual.getReverseLatency().intValue());

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldFailTier2AndSendRerouteRequest() {
        transactionManager.doInTransaction(() -> {
            Flow flowSetup = flowRepository.findById(flow.getFlowId())
                    .orElseThrow(() -> new IllegalStateException("Flow not found"));
            flowSetup.setPathComputationStrategy(PathComputationStrategy.MAX_LATENCY);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatencyTier2() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_2_FAILED.equals(fsm.getCurrentState())));
        FlowStats actual = flowStatsRepository.findByFlowId(flow.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        assertEquals(latency.getNano(), actual.getForwardLatency().intValue());
        assertEquals(latency.minus(NANOSECOND).getNano(), actual.getReverseLatency().intValue());

        verify(carrier, times(2)).sendFlowRerouteRequest(flow.getFlowId());
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldFailTier2AndDoNotSendRerouteRequestForCostStrategy() {
        transactionManager.doInTransaction(() -> {
            Flow flowSetup = flowRepository.findById(flow.getFlowId())
                    .orElseThrow(() -> new IllegalStateException("Flow not found"));
            flowSetup.setPathComputationStrategy(PathComputationStrategy.COST);
        });

        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, NANOSECOND);

        Duration latency = Duration.ofNanos((long) (flow.getMaxLatencyTier2() * (1 + THRESHOLD)) + 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, latency);
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, latency.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> TIER_2_FAILED.equals(fsm.getCurrentState())));
        FlowStats actual = flowStatsRepository.findByFlowId(flow.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        assertEquals(latency.getNano(), actual.getForwardLatency().intValue());
        assertEquals(latency.minus(NANOSECOND).getNano(), actual.getReverseLatency().intValue());

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldBecomeHealthyAndSendSyncRequest() {
        Duration tier2Failed = Duration.ofNanos(flow.getMaxLatencyTier2() * 2);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, tier2Failed);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, tier2Failed);

        Duration healthy = Duration.ofNanos((long) (flow.getMaxLatency() * (1 - THRESHOLD)) - 5);

        for (int i = 0; i < 10; i++) {
            clock.adjust(Duration.ofSeconds(10));
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, healthy);
            service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, healthy.minus(NANOSECOND));
            service.processTick(0);
            if (i == 0) {
                assertTrue(service.fsms.values().stream().allMatch(fsm -> UNSTABLE.equals(fsm.getCurrentState())));
            }
        }

        assertEquals(2, service.fsms.values().size());
        assertTrue(service.fsms.values().stream().allMatch(fsm -> HEALTHY.equals(fsm.getCurrentState())));
        FlowStats actual = flowStatsRepository.findByFlowId(flow.getFlowId())
                .orElseThrow(() -> new IllegalStateException("Flow not found"));
        assertEquals(healthy.getNano(), actual.getForwardLatency().intValue());
        assertEquals(healthy.minus(NANOSECOND).getNano(), actual.getReverseLatency().intValue());

        verify(carrier, times(2)).sendFlowSyncRequest(flow.getFlowId());
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldUpdateFlowInfo() {
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, NANOSECOND);

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

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldRemoveFlowInfo() {
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.FORWARD, NANOSECOND);
        service.processFlowLatencyMeasurement(flow.getFlowId(), FlowDirection.REVERSE, NANOSECOND);

        service.removeFlowInfo(flow.getFlowId());

        assertTrue(service.fsms.values().isEmpty());

        verifyNoMoreInteractions(carrier);
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
}
