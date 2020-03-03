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

package org.openkilda.wfm.topology.network.controller;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmContext;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmEvent;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmState;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;
import org.openkilda.wfm.topology.network.service.IUniIslCarrier;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

@Slf4j
public class UniIslFsm extends AbstractBaseFsm<UniIslFsm, UniIslFsmState,
        UniIslFsmEvent, UniIslFsmContext> {
    private final Endpoint endpoint;
    private IslReference islReference;
    private IslDataHolder islData = null;

    public static UniIslFsmFactory factory() {
        return new UniIslFsmFactory();
    }

    public UniIslFsm(Endpoint endpoint) {
        this.endpoint = endpoint;

        islReference = IslReference.of(endpoint);
    }

    // -- FSM actions --

    public void handleActivate(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event,
                               UniIslFsmContext context) {
        Isl history = context.getHistory();
        if (history != null) {
            islReference = IslReference.of(history);
            islData = new IslDataHolder(history);
            context.getOutput().setupIslFromHistory(endpoint, islReference, history);
        }
    }

    public void doDiscoveryChoice(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event,
                                  UniIslFsmContext context) {
        IslReference actualReference = IslReference.of(context.getDiscoveryEvent());
        if (islReference.equals(actualReference)) {
            fire(UniIslFsmEvent._DISCOVERY_CHOICE_SAME, context);
        } else {
            fire(UniIslFsmEvent._DISCOVERY_CHOICE_MOVED, context);
        }
    }

    public void doSelfLoopChoice(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event,
                                 UniIslFsmContext context) {
        IslReference reference = IslReference.of(context.getDiscoveryEvent());
        if (reference.isSelfLoop()) {
            log.error("Self looped ISL discovery received: {}", reference);
            fire(UniIslFsmEvent._SELF_LOOP_CHOICE_TRUE, context);
        } else {
            fire(UniIslFsmEvent._SELF_LOOP_CHOICE_FALSE, context);
        }
    }

    public void handleMoved(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        if (!islReference.isIncomplete()) {
            emitIslMove(context);
        } else {
            log.debug("Do not emit ISL move for incomplete ISL reference {}", islReference);
        }
    }

    public void proxyRoundTripStatus(
            UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        emitRoundTripStatus(context.getOutput(), context.getRoundTripStatus());
    }

    public void upEnter(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        IslInfoData discovery = context.getDiscoveryEvent();
        if (discovery != null) {
            islReference = IslReference.of(discovery);
            islData = new IslDataHolder(discovery);
        }
        emitIslUp(context);
    }

    public void downEnter(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        if (!islReference.isIncomplete()) {
            emitIslDown(context, mapDownReason(event));
        }
    }

    public void downProxyRoundTripStatus(
            UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        if (!islReference.isIncomplete()) {
            emitRoundTripStatus(context.getOutput(), context.getRoundTripStatus());
        }
    }


    public void bfdEnter(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        emitIslUp(context);
    }

    // -- private/service methods --

    private void emitIslUp(UniIslFsmContext context) {
        context.getOutput().notifyIslUp(endpoint, islReference, islData);
    }

    private void emitIslDown(UniIslFsmContext context, IslDownReason reason) {
        context.getOutput().notifyIslDown(endpoint, islReference, reason);
    }

    private void emitIslMove(UniIslFsmContext context) {
        context.getOutput().notifyIslMove(endpoint, islReference);
    }

    private void emitRoundTripStatus(IUniIslCarrier carrier, RoundTripStatus status) {
        carrier.notifyIslRoundTripStatus(islReference, status);
    }

    private IslDownReason mapDownReason(UniIslFsmEvent event) {
        IslDownReason reason;
        switch (event) {
            case FAIL:
                reason = IslDownReason.POLL_TIMEOUT;
                break;
            case PHYSICAL_DOWN:
                reason = IslDownReason.PORT_DOWN;
                break;
            case BFD_DOWN:
                reason = IslDownReason.BFD_DOWN;
                break;

            default:
                throw new IllegalArgumentException(String.format(
                        "Unable to map %s.%s into %s",
                        UniIslFsmEvent.class.getName(), event, IslDownReason.class.getName()));
        }

        return reason;
    }

    // -- service data types --

    public static class UniIslFsmFactory {
        private final StateMachineBuilder<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> builder;

        UniIslFsmFactory() {
            builder = StateMachineBuilderFactory.create(
                    UniIslFsm.class, UniIslFsmState.class, UniIslFsmEvent.class, UniIslFsmContext.class,
                    // extra parameters
                    Endpoint.class);

            final String proxyRoundTripStatusMethod = "proxyRoundTripStatus";

            // INIT
            builder.transition()
                    .from(UniIslFsmState.INIT).to(UniIslFsmState.UNKNOWN).on(UniIslFsmEvent.ACTIVATE)
                    .callMethod("handleActivate");

            // UNKNOWN
            builder.transition()
                    .from(UniIslFsmState.UNKNOWN).to(UniIslFsmState.DISCOVERY_CHOICE).on(UniIslFsmEvent.DISCOVERY);
            builder.transition()
                    .from(UniIslFsmState.UNKNOWN).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.FAIL);
            builder.transition()
                    .from(UniIslFsmState.UNKNOWN).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.PHYSICAL_DOWN);

            // DISCOVERY_CHOICE
            builder.transition()
                    .from(UniIslFsmState.DISCOVERY_CHOICE).to(UniIslFsmState.SELF_LOOP_CHOICE)
                    .on(UniIslFsmEvent._DISCOVERY_CHOICE_MOVED)
                    .callMethod("handleMoved");
            builder.transition()
                    .from(UniIslFsmState.DISCOVERY_CHOICE).to(UniIslFsmState.UP)
                    .on(UniIslFsmEvent._DISCOVERY_CHOICE_SAME);
            builder.onEntry(UniIslFsmState.DISCOVERY_CHOICE)
                    .callMethod("doDiscoveryChoice");

            // SELF_LOOP_CHOICE
            builder.transition()
                    .from(UniIslFsmState.SELF_LOOP_CHOICE).to(UniIslFsmState.UP)
                    .on(UniIslFsmEvent._SELF_LOOP_CHOICE_FALSE);
            builder.transition()
                    .from(UniIslFsmState.SELF_LOOP_CHOICE).to(UniIslFsmState.UNKNOWN)
                    .on(UniIslFsmEvent._SELF_LOOP_CHOICE_TRUE);
            builder.onEntry(UniIslFsmState.SELF_LOOP_CHOICE)
                    .callMethod("doSelfLoopChoice");

            // UP
            builder.transition()
                    .from(UniIslFsmState.UP).to(UniIslFsmState.DISCOVERY_CHOICE).on(UniIslFsmEvent.DISCOVERY);
            builder.transition()
                    .from(UniIslFsmState.UP).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.FAIL);
            builder.transition()
                    .from(UniIslFsmState.UP).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.PHYSICAL_DOWN);
            builder.transition()
                    .from(UniIslFsmState.UP).to(UniIslFsmState.BFD).on(UniIslFsmEvent.BFD_UP);
            builder.onEntry(UniIslFsmState.UP)
                    .callMethod("upEnter");
            builder.internalTransition().within(UniIslFsmState.UP).on(UniIslFsmEvent.ROUND_TRIP_STATUS)
                    .callMethod(proxyRoundTripStatusMethod);

            // DOWN
            builder.transition()
                    .from(UniIslFsmState.DOWN).to(UniIslFsmState.DISCOVERY_CHOICE).on(UniIslFsmEvent.DISCOVERY);
            builder.transition()
                    .from(UniIslFsmState.DOWN).to(UniIslFsmState.BFD).on(UniIslFsmEvent.BFD_UP);
            builder.onEntry(UniIslFsmState.DOWN)
                    .callMethod("downEnter");
            builder.internalTransition().within(UniIslFsmState.DOWN).on(UniIslFsmEvent.ROUND_TRIP_STATUS)
                    .callMethod("downProxyRoundTripStatus");

            // BFD
            builder.transition()
                    .from(UniIslFsmState.BFD).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.PHYSICAL_DOWN);
            builder.transition()
                    .from(UniIslFsmState.BFD).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.BFD_DOWN);
            builder.transition()
                    .from(UniIslFsmState.BFD).to(UniIslFsmState.UP).on(UniIslFsmEvent.BFD_KILL);
            builder.onEntry(UniIslFsmState.BFD)
                    .callMethod("bfdEnter");
            builder.internalTransition().within(UniIslFsmState.BFD).on(UniIslFsmEvent.ROUND_TRIP_STATUS)
                    .callMethod(proxyRoundTripStatusMethod);
        }

        public FsmExecutor<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> produceExecutor() {
            return new FsmExecutor<>(UniIslFsmEvent.NEXT);
        }

        public UniIslFsm produce(Endpoint endpoint) {
            return builder.newStateMachine(UniIslFsmState.INIT, endpoint);
        }
    }

    @Value
    @Builder
    public static class UniIslFsmContext {
        private final IUniIslCarrier output;

        private Isl history;
        private IslInfoData discoveryEvent;
        private RoundTripStatus roundTripStatus;

        public static UniIslFsmContextBuilder builder(IUniIslCarrier carrier) {
            return new UniIslFsmContextBuilder()
                    .output(carrier);
        }
    }

    public enum UniIslFsmEvent {
        NEXT, ACTIVATE,

        PHYSICAL_DOWN,
        DISCOVERY, FAIL, ROUND_TRIP_STATUS,
        BFD_UP, BFD_DOWN, BFD_KILL,

        _DISCOVERY_CHOICE_SAME, _DISCOVERY_CHOICE_MOVED,
        _SELF_LOOP_CHOICE_TRUE, _SELF_LOOP_CHOICE_FALSE
    }

    public enum UniIslFsmState {
        INIT,
        UNKNOWN,

        DISCOVERY_CHOICE,
        SELF_LOOP_CHOICE,

        UP, DOWN,
        BFD
    }
}
