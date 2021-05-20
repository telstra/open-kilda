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

package org.openkilda.wfm.topology.flowmonitoring.fsm;

import static java.lang.String.format;

import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Context;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Event;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State;
import org.openkilda.wfm.topology.flowmonitoring.service.FlowSlaMonitoringCarrier;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
public class FlowLatencyMonitoringFsm extends AbstractBaseFsm<FlowLatencyMonitoringFsm, State, Event, Context> {

    private final Clock clock;
    private final Duration timeout;
    private final String flowId;
    private final String direction;
    private final long maxLatency;
    private final long maxLatencyTier2;

    private State lastStableState = State._INIT;

    private State lastEventType;
    private Instant lastEventTimestamp;

    private long currentLatency;

    public static FlowLatencyMonitoringFsmFactory factory(Clock clock, Duration timeout) {
        return new FlowLatencyMonitoringFsmFactory(clock, timeout);
    }

    public FlowLatencyMonitoringFsm(Clock clock, Duration timeout, String flowId, String direction,
                                    Long maxLatency, Long maxLatencyTier2) {
        this.clock = clock;
        this.timeout = timeout;
        this.flowId = flowId;
        this.direction = direction;
        this.maxLatency = maxLatency;
        this.maxLatencyTier2 = maxLatencyTier2;
    }

    public void enterHealthy(State from, State to, Event event, Context context) {
        lastStableState = State.HEALTHY;
        saveCurrentLatency(from, to, event, context);
        persistCurrentLatency(from, to, event, context);
    }

    public void enterTier1Failed(State from, State to, Event event, Context context) {
        lastStableState = State.TIER_1_FAILED;
        saveCurrentLatency(from, to, event, context);
        persistCurrentLatency(from, to, event, context);
    }

    public void enterTier2Failed(State from, State to, Event event, Context context) {
        lastStableState = State.TIER_2_FAILED;
        saveCurrentLatency(from, to, event, context);
        persistCurrentLatency(from, to, event, context);
    }

    public void enterUnstable(State from, State to, Event event, Context context) {
        saveLastEventInfo(from);
    }

    public void saveHealthyEventInfo(State from, State to, Event event, Context context) {
        saveLastEventInfo(State.HEALTHY);
    }

    public void saveTier1FailedEventInfo(State from, State to, Event event, Context context) {
        saveLastEventInfo(State.TIER_1_FAILED);
    }

    public void processTick(State from, State to, Event event, Context context) {
        Instant current = clock.instant();
        if (current.isAfter(lastEventTimestamp.plus(timeout))) {
            switch (lastEventType) {
                case HEALTHY:
                    fire(Event.STABLE_HEALTHY);
                    break;
                case TIER_1_FAILED:
                    fire(Event.STABLE_TIER_1_FAILED);
                    break;
                case TIER_2_FAILED:
                    fire(Event.STABLE_TIER_2_FAILED);
                    break;
                default:
                    throw new IllegalStateException(format("Illegal last event type %s", lastEventType));
            }
        }
    }

    public void sendFlowSyncRequest(State from, State to, Event event, Context context) {
        log.info("Flow {} {} latency moved to healthy. Sending sync request.", flowId, direction);
        context.getCarrier().sendFlowSyncRequest(flowId);
    }

    public void sendFlowRerouteRequest(State from, State to, Event event, Context context) {
        log.info("Flow {} {} latency moved to unhealthy. Sending reroute request if required.", flowId, direction);
        context.getCarrier().sendFlowRerouteRequest(flowId);
    }

    public void saveTier2FailedEventInfo(State from, State to, Event event, Context context) {
        saveLastEventInfo(State.TIER_2_FAILED);
    }

    public void saveCurrentLatency(State from, State to, Event event, Context context) {
        currentLatency = context.getLatency();
    }

    public void persistCurrentLatency(State from, State to, Event event, Context context) {
        context.getCarrier().saveFlowLatency(flowId, direction, currentLatency);
    }

    private void saveLastEventInfo(State event) {
        lastEventType = event;
        lastEventTimestamp = clock.instant();
    }

    public static class FlowLatencyMonitoringFsmFactory {
        private final StateMachineBuilder<FlowLatencyMonitoringFsm, State, Event, Context> builder;

        private final Clock clock;
        private final Duration timeout;

        FlowLatencyMonitoringFsmFactory(Clock clock, Duration timeout) {
            this.clock = clock;
            this.timeout = timeout;
            builder = StateMachineBuilderFactory.create(
                    FlowLatencyMonitoringFsm.class, State.class, Event.class, Context.class,
                    // extra parameters
                    Clock.class, Duration.class, String.class, String.class, Long.class, Long.class);

            // INIT
            builder.transition()
                    .from(State._INIT).to(State.HEALTHY).on(Event.HEALTHY);
            builder.transition()
                    .from(State._INIT).to(State.TIER_1_FAILED).on(Event.TIER_1_FAILED);
            builder.transition()
                    .from(State._INIT).to(State.TIER_2_FAILED).on(Event.TIER_2_FAILED);

            // HEALTHY
            builder.onEntry(State.HEALTHY)
                    .callMethod("enterHealthy");
            builder.internalTransition()
                    .within(State.HEALTHY).on(Event.HEALTHY)
                    .callMethod("saveCurrentLatency");
            builder.internalTransition()
                    .within(State.HEALTHY).on(Event.TICK)
                    .callMethod("persistCurrentLatency");
            builder.transition()
                    .from(State.HEALTHY).to(State.UNSTABLE).on(Event.TIER_1_FAILED);
            builder.transition()
                    .from(State.HEALTHY).to(State.UNSTABLE).on(Event.TIER_2_FAILED);

            // TIER 1 FAILED
            builder.onEntry(State.TIER_1_FAILED)
                    .callMethod("enterTier1Failed");
            builder.internalTransition()
                    .within(State.TIER_1_FAILED).on(Event.TIER_1_FAILED)
                    .callMethod("saveCurrentLatency");
            builder.internalTransition()
                    .within(State.TIER_1_FAILED).on(Event.TICK)
                    .callMethod("persistCurrentLatency");
            builder.transition()
                    .from(State.TIER_1_FAILED).to(State.UNSTABLE).on(Event.HEALTHY);
            builder.transition()
                    .from(State.TIER_1_FAILED).to(State.UNSTABLE).on(Event.TIER_2_FAILED);

            // TIER 2 FAILED
            builder.onEntry(State.TIER_2_FAILED)
                    .callMethod("enterTier2Failed");
            builder.internalTransition()
                    .within(State.TIER_2_FAILED).on(Event.TIER_2_FAILED)
                    .callMethod("saveCurrentLatency");
            builder.internalTransition()
                    .within(State.TIER_2_FAILED).on(Event.TICK)
                    .callMethod("persistCurrentLatency");
            builder.transition()
                    .from(State.TIER_2_FAILED).to(State.UNSTABLE).on(Event.HEALTHY);
            builder.transition()
                    .from(State.TIER_2_FAILED).to(State.UNSTABLE).on(Event.TIER_1_FAILED);

            // UNSTABLE
            builder.onEntry(State.UNSTABLE)
                    .callMethod("enterUnstable");
            builder.internalTransition()
                    .within(State.UNSTABLE).on(Event.HEALTHY)
                    .callMethod("saveHealthyEventInfo");
            builder.internalTransition()
                    .within(State.UNSTABLE).on(Event.TIER_1_FAILED)
                    .callMethod("saveTier1FailedEventInfo");
            builder.internalTransition()
                    .within(State.UNSTABLE).on(Event.TIER_2_FAILED)
                    .callMethod("saveTier2FailedEventInfo");
            builder.internalTransition()
                    .within(State.UNSTABLE).on(Event.TICK)
                    .callMethod("processTick");

            builder.transition()
                    .from(State.UNSTABLE).to(State.HEALTHY).on(Event.STABLE_HEALTHY)
                    .callMethod("sendFlowSyncRequest");
            builder.transition()
                    .from(State.UNSTABLE).to(State.TIER_1_FAILED).on(Event.STABLE_TIER_1_FAILED)
                    .callMethod("sendFlowRerouteRequest");
            builder.transition()
                    .from(State.UNSTABLE).to(State.TIER_2_FAILED).on(Event.STABLE_TIER_2_FAILED)
                    .callMethod("sendFlowRerouteRequest");
        }

        public FsmExecutor<FlowLatencyMonitoringFsm, State, Event, Context> produceExecutor() {
            return new FsmExecutor<>(Event.NEXT);
        }

        public FlowLatencyMonitoringFsm produce(String flowId, String direction,
                                                long maxLatency, long maxLatencyTier2) {
            return builder.newStateMachine(State._INIT, clock, timeout, flowId, direction,
                    maxLatency, maxLatencyTier2);
        }
    }

    public long getMaxLatency() {
        return maxLatency;
    }

    public long getMaxLatencyTier2() {
        return maxLatencyTier2;
    }

    public State getLastStableState() {
        return lastStableState;
    }

    @Value
    @Builder
    public static class Context {
        long latency;

        FlowSlaMonitoringCarrier carrier;
    }

    public enum Event {
        NEXT,

        TICK,

        HEALTHY, TIER_1_FAILED, TIER_2_FAILED,

        STABLE_HEALTHY, STABLE_TIER_1_FAILED, STABLE_TIER_2_FAILED
    }

    public enum State {
        _INIT,

        HEALTHY, TIER_1_FAILED, TIER_2_FAILED,

        UNSTABLE
    }
}
