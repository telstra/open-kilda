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

package org.openkilda.wfm.topology.network.controller;

import org.openkilda.wfm.share.history.model.PortHistoryEvent;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.AntiFlapFsm.Context;
import org.openkilda.wfm.topology.network.controller.AntiFlapFsm.Event;
import org.openkilda.wfm.topology.network.controller.AntiFlapFsm.State;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.service.IAntiFlapCarrier;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.io.Serializable;
import java.time.Instant;

@Slf4j
public final class AntiFlapFsm extends AbstractBaseFsm<AntiFlapFsm, State, Event, Context>  {
    private final Endpoint endpoint;
    private final long delayWarmUp;
    private final long delayCoolingDown;
    private final long delayMin;
    private final long statsDumpingInterval;

    private long downTime = 0;
    private long upTime = 0;
    private long startTime = 0;

    private long lastStatsSent = 0L;
    private int upEventsCount = 0;
    private int downEventsCount = 0;

    public static AntiFlapFsmFactory factory() {
        return new AntiFlapFsmFactory();
    }

    public AntiFlapFsm(Config config) {
        endpoint = config.getEndpoint();
        delayCoolingDown = config.getDelayCoolingDown();
        delayWarmUp = config.getDelayWarmUp();
        delayMin = config.getDelayMin();
        statsDumpingInterval = config.getAntiFlapStatsDumpingInterval();

        log.debug("{}", config);
    }

    // -- FSM actions --

    public void emitPortUpAndSaveTime(State from, State to, Event event, Context context) {
        emitPortUp(context);
        savePortUpTime(from, to, event, context);
    }

    public void emitPortDownAndSaveTime(State from, State to, Event event, Context context) {
        log.trace("emitPortDownAndSaveTime {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        emitPortDown(context);
        savePortDownTime(from, to, event, context);
    }

    public void portUpOnNothing(State from, State to, Event event, Context context) {
        emitPortUp(context);
        savePortUpTime(from, to, event, context);
    }

    public void portDownOnNothing(State from, State to, Event event, Context context) {
        log.debug("portDownOnNothing {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        if (upWasLast()) {
            fire(Event.TO_WARMING_UP, context);
        }
    }

    private void emitPortUp(Context context) {
        log.debug("Emit physical port {} become {}", endpoint, LinkStatus.UP);
        context.getOutput().filteredLinkStatus(endpoint, LinkStatus.UP);
    }

    public void saveStartTimeAndDownTime(State from, State to, Event event, Context context) {
        log.trace("saveStartTimeAndDownTime {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".",
                endpoint, from, to, event, context);
        startTime = downTime = context.getTime();
        upTime = downTime - 1;
        log.debug("Physical port {} become DOWN on {}", endpoint, downTime);
    }

    public void handlePortUpWhileCoolingDown(State from, State to, Event event, Context context) {
        log.debug("handlePortUpWhileCoolingDown {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        upEventsCount++;
        savePortUpTime(from, to, event, context);
    }

    public void handlePortDownWhileCoolingDown(State from, State to, Event event, Context context) {
        log.debug("handlePortDownWhileCoolingDown {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        downEventsCount++;
        savePortDownTime(from, to, event, context);
    }

    public void savePortUpTime(State from, State to, Event event, Context context) {
        log.debug("savePortUpTime {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        upTime = context.getTime();

        if (downTime > upTime) {
            log.debug("Physical port {} fix for port-up uptime: {} downtime: {}", endpoint, upTime, downTime);
            downTime = upTime - 1;
        }

        log.debug("Physical port {} become UP on {}", endpoint, upTime);
    }

    public void savePortDownTime(State from, State to, Event event, Context context) {
        log.debug("savePortDownTime {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        downTime = context.getTime();

        if (upTime > downTime) {
            log.debug("Physical port {} fix for port-down uptime: {} downtime: {}", endpoint, upTime, downTime);
            upTime = downTime - 1;
        }

        log.debug("Physical port {} become DOWN on {}", endpoint, downTime);
    }

    private void exitCoolingDown(Context context) {
        if (upWasLast()) {
            emitPortUp(context);
        }
    }

    public void tickOnWarmingUp(State from, State to, Event event, Context context) {
        log.trace("tickOnWarmingUp {} from \"{}\" to \"{}\" on \"{}\" on time \"{}\" start {} down {} up {} ", endpoint,
                from, to, event, context.getTime(), startTime, downTime, upTime);
        long now = context.getTime();

        if (downWasLast() && now - downTime > delayMin) {
            fire(Event.TO_COOLING_DOWN, context);
            log.trace("tickOnWarmingUp TO_COOLING_DOWN");
        } else if (now - startTime > delayWarmUp) {
            if (downWasLast() || last() > startTime + delayWarmUp - delayMin) {
                fire(Event.TO_COOLING_DOWN, context);
                log.trace("tickOnWarmingUp TO_COOLING_DOWN 2");
            } else {
                fire(Event.TO_NOTHING, context);
                log.trace("tickOnWarmingUp TO_NOTHING");
            }
        }
    }

    private void emitPortDown(Context context) {
        log.debug("Emit physical port {} become {}", endpoint, LinkStatus.DOWN);
        context.getOutput().filteredLinkStatus(endpoint, LinkStatus.DOWN);
    }

    public void tickCoolingDown(State from, State to, Event event, Context context) {
        log.trace("tickCoolingDown {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        long now = context.getTime();

        if (now - last() > delayCoolingDown) {
            fire(Event.TO_NOTHING, context);
        } else if (statsDumpingInterval > 0 && now - lastStatsSent > statsDumpingInterval) {
            lastStatsSent = now;
            context.getOutput().sendAntiFlapStatsPortHistoryEvent(endpoint, PortHistoryEvent.ANTI_FLAP_PERIODIC_STATS,
                    Instant.now(), upEventsCount, downEventsCount);
        }
    }

    public void activateAntiFlap(State from, State to, Event event, Context context) {
        emitPortDown(context);

        context.getOutput().sendAntiFlapPortHistoryEvent(endpoint, PortHistoryEvent.ANTI_FLAP_ACTIVATED, Instant.now());
        lastStatsSent = context.getTime();
    }

    public void deactivateAntiFlap(State from, State to, Event event, Context context) {
        log.debug("deactivateAntiFlap {} from \"{}\" to \"{}\" on \"{}\" with context \"{}\".", endpoint,
                from, to, event, context);
        exitCoolingDown(context);
        Instant timeNow = Instant.now();
        context.getOutput().sendAntiFlapStatsPortHistoryEvent(endpoint, PortHistoryEvent.ANTI_FLAP_DEACTIVATED, timeNow,
                upEventsCount, downEventsCount);

        upEventsCount = 0;
        downEventsCount = 0;
    }

    // -- private/service methods --

    private boolean upWasLast() {
        return upTime > downTime;
    }

    private boolean downWasLast() {
        return !upWasLast();
    }

    private long last() {
        return Math.max(downTime, upTime);
    }

    public static class AntiFlapFsmFactory {
        private final StateMachineBuilder<AntiFlapFsm, State, Event, Context> builder;

        AntiFlapFsmFactory() {
            builder = StateMachineBuilderFactory.create(
                    AntiFlapFsm.class, State.class, Event.class, Context.class,
                    // extra parameters
                    Config.class);

            // INIT
            builder.transition()
                    .from(State.INIT).to(State.NOTHING).on(Event.PORT_UP)
                    .callMethod("emitPortUpAndSaveTime");
            builder.transition()
                    .from(State.INIT).to(State.NOTHING).on(Event.PORT_DOWN)
                    .callMethod("emitPortDownAndSaveTime");

            // NOTHING
            builder.internalTransition()
                    .within(State.NOTHING).on(Event.PORT_UP)
                    .callMethod("portUpOnNothing");
            builder.internalTransition()
                    .within(State.NOTHING).on(Event.PORT_DOWN)
                    .callMethod("portDownOnNothing");
            builder.transition()
                    .from(State.NOTHING).to(State.WARMING_UP).on(Event.TO_WARMING_UP);

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
                    .callMethod("activateAntiFlap");
            builder.onExit(State.COOLING_DOWN)
                    .callMethod("deactivateAntiFlap");
            builder.internalTransition().within(State.COOLING_DOWN).on(Event.PORT_UP)
                    .callMethod("handlePortUpWhileCoolingDown");
            builder.internalTransition().within(State.COOLING_DOWN).on(Event.PORT_DOWN)
                    .callMethod("handlePortDownWhileCoolingDown");
            builder.internalTransition().within(State.COOLING_DOWN).on(Event.TICK)
                    .callMethod("tickCoolingDown");
            builder.transition()
                    .from(State.COOLING_DOWN).to(State.NOTHING).on(Event.TO_NOTHING);
        }

        public FsmExecutor<AntiFlapFsm, State, Event, Context> produceExecutor() {
            return new FsmExecutor<>(Event.NEXT);
        }

        public AntiFlapFsm produce(Config config) {
            return builder.newStateMachine(State.INIT, config);
        }
    }

    public enum Event {
        NEXT,

        PORT_UP, PORT_DOWN, TICK,

        TO_COOLING_DOWN, TO_NOTHING, TO_WARMING_UP
    }

    public enum State {
        INIT, NOTHING, WARMING_UP, COOLING_DOWN
    }

    @Data
    @Builder(toBuilder = true)
    public static class Config implements Serializable {
        private final Endpoint endpoint;
        private final long delayWarmUp;
        private final long delayCoolingDown;
        private final long delayMin;
        private final long antiFlapStatsDumpingInterval;
    }

    @Data
    public static class Context {
        private final IAntiFlapCarrier output;
        private final Long time;
    }
}
