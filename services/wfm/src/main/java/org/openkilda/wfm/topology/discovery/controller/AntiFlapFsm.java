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

package org.openkilda.wfm.topology.discovery.controller;

import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.AntiFlapFsm.Context;
import org.openkilda.wfm.topology.discovery.controller.AntiFlapFsm.Event;
import org.openkilda.wfm.topology.discovery.controller.AntiFlapFsm.State;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts.LinkStatus;
import org.openkilda.wfm.topology.discovery.service.IAntiFlapCarrier;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

@Slf4j
public final class AntiFlapFsm extends AbstractStateMachine<AntiFlapFsm, State, Event, Context> {

    private static final StateMachineBuilder<AntiFlapFsm, State, Event, Context> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                AntiFlapFsm.class, State.class, Event.class, Context.class,
                // extra parameters
                Config.class);

        // NOTHING
        builder.internalTransition().within(State.NOTHING).on(Event.PORT_UP)
                .callMethod("emitPortUp");
        builder.transition()
                .from(State.NOTHING).to(State.WARMING_UP).on(Event.PORT_DOWN);

        // State.WARMING_UP
        builder.onEntry(State.WARMING_UP)
                .callMethod("saveStartTimeAndDownTime");
        builder.internalTransition().within(State.WARMING_UP).on(Event.PORT_UP)
                .callMethod("savePortUpTime");
        builder.internalTransition().within(State.WARMING_UP).on(Event.PORT_DOWN)
                .callMethod("savePortDownTime");
        builder.internalTransition().within(State.WARMING_UP).on(Event.TICK)
                .callMethod("tickOnWarmingUp");
        builder.transition()
                .from(State.WARMING_UP).to(State.NOTHING).on(Event.TO_NOTHING);
        builder.transition()
                .from(State.WARMING_UP).to(State.COOLING_DOWN).on(Event.TO_COOLING_DOWN);

        // State.COOLING_DOWN
        builder.onEntry(State.COOLING_DOWN)
                .callMethod("emitPortDown");
        builder.onExit(State.COOLING_DOWN)
                .callMethod("exitCoolingDown");
        builder.internalTransition().within(State.COOLING_DOWN).on(Event.PORT_UP)
                .callMethod("savePortUpTime");
        builder.internalTransition().within(State.COOLING_DOWN).on(Event.PORT_DOWN)
                .callMethod("savePortDownTime");
        builder.internalTransition().within(State.COOLING_DOWN).on(Event.TICK)
                .callMethod("tickCoolingDown");
        builder.transition()
                .from(State.COOLING_DOWN).to(State.NOTHING).on(Event.TO_NOTHING);
    }

    private final Endpoint endpoint;
    private final long delayWarmUp;
    private final long delayCoolingDown;
    private final long delayMin;

    private long downTime;
    private long upTime;
    private long startTime;

    public AntiFlapFsm(Config config) {
        endpoint = config.getEndpoint();
        delayCoolingDown = config.getDelayCoolingDown();
        delayWarmUp = config.getDelayWarmUp();
        delayMin = config.getDelayMin();
    }

    public static FsmExecutor<AntiFlapFsm, State, Event, Context> makeExecutor() {
        return new FsmExecutor<>(Event.NEXT);
    }

    public static AntiFlapFsm create(Config config) {
        return builder.newStateMachine(State.NOTHING, config);
    }

    // -- FSM actions --

    protected void emitPortUp(State from, State to, Event event, Context context) {
        log.debug("Filter physical port {} become {}", endpoint, LinkStatus.UP);
        context.getOutput().filteredLinkStatus(endpoint, LinkStatus.UP);
    }

    protected void saveStartTimeAndDownTime(State from, State to, Event event, Context context) {
        startTime = downTime = context.getTime();
    }

    protected void savePortUpTime(State from, State to, Event event, Context context) {
        upTime = context.getTime();
    }

    protected void savePortDownTime(State from, State to, Event event, Context context) {
        downTime = context.getTime();
    }

    protected void exitCoolingDown(State from, State to, Event event, Context context) {
        if (upWasLast()) {
            emitPortUp(from, to, event, context);
        }
    }

    protected void tickOnWarmingUp(State from, State to, Event event, Context context) {
        long now = context.getTime();

        if (donwWasLast() && now - downTime > delayMin) {
            fire(Event.TO_COOLING_DOWN, context);
        } else if (now - startTime > delayWarmUp) {
            if (donwWasLast() || last() > startTime + delayWarmUp - delayMin) {
                fire(Event.TO_COOLING_DOWN, context);
            } else {
                fire(Event.TO_NOTHING, context);
            }
        }
    }

    protected void emitPortDown(State from, State to, Event event, Context context) {
        log.debug("Filter physical port {} become {}", endpoint, LinkStatus.DOWN);
        context.getOutput().filteredLinkStatus(endpoint, LinkStatus.DOWN);
    }

    protected void tickCoolingDown(State from, State to, Event event, Context context) {
        long now = context.getTime();

        if (now - last() > delayCoolingDown) {
            fire(Event.TO_NOTHING, context);
        }

    }

    // -- private/service methods --

    private boolean upWasLast() {
        return upTime > downTime;
    }

    private boolean donwWasLast() {
        return !upWasLast();
    }

    private long last() {
        return Math.max(downTime, upTime);
    }

    public enum Event {
        NEXT,

        PORT_UP, PORT_DOWN, TO_COOLING_DOWN, TO_NOTHING, TICK
    }

    public enum State {
        NOTHING, WARMING_UP, COOLING_DOWN
    }

    @Data
    @Builder(toBuilder = true)
    public static class Config {
        private final Endpoint endpoint;
        private final long delayWarmUp;
        private final long delayCoolingDown;
        private final long delayMin;
    }

    @Data
    public static class Context {
        private final IAntiFlapCarrier output;
        private final Long time;
    }
}
