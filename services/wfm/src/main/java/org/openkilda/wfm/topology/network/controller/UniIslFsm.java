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
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmContext;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmEvent;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmState;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.IslReference;
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

    private static final StateMachineBuilder<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                UniIslFsm.class, UniIslFsmState.class, UniIslFsmEvent.class, UniIslFsmContext.class,
                // extra parameters
                Endpoint.class);

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
                .from(UniIslFsmState.DISCOVERY_CHOICE).to(UniIslFsmState.UP).on(UniIslFsmEvent._DISCOVERY_CHOICE_MOVED)
                .callMethod("handleMoved");
        builder.transition()
                .from(UniIslFsmState.DISCOVERY_CHOICE).to(UniIslFsmState.UP).on(UniIslFsmEvent._DISCOVERY_CHOICE_SAME);
        builder.onEntry(UniIslFsmState.DISCOVERY_CHOICE)
                .callMethod("makeDiscoveryChoice");

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

        // DOWN
        builder.transition()
                .from(UniIslFsmState.DOWN).to(UniIslFsmState.DISCOVERY_CHOICE).on(UniIslFsmEvent.DISCOVERY);
        builder.transition()
                .from(UniIslFsmState.DOWN).to(UniIslFsmState.BFD).on(UniIslFsmEvent.BFD_UP);
        builder.onEntry(UniIslFsmState.DOWN)
                .callMethod("downEnter");

        // BFD
        builder.transition()
                .from(UniIslFsmState.BFD).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.PHYSICAL_DOWN);
        builder.transition()
                .from(UniIslFsmState.BFD).to(UniIslFsmState.DOWN).on(UniIslFsmEvent.BFD_DOWN);
        builder.transition()
                .from(UniIslFsmState.BFD).to(UniIslFsmState.UP).on(UniIslFsmEvent.BFD_KILL);
        builder.onEntry(UniIslFsmState.BFD)
                .callMethod("bfdEnter");
    }

    public static FsmExecutor<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> makeExecutor() {
        return new FsmExecutor<>(UniIslFsmEvent.NEXT);
    }

    public static UniIslFsm create(Endpoint endpoint) {
        return builder.newStateMachine(UniIslFsmState.INIT, endpoint);
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
            context.getOutput().setupIslFromHistory(endpoint, islReference, history);
        }
    }

    public void makeDiscoveryChoice(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event,
                                    UniIslFsmContext context) {
        IslReference actualReference = IslReference.of(context.getDiscoveryEvent());
        if (islReference.equals(actualReference)) {
            fire(UniIslFsmEvent._DISCOVERY_CHOICE_SAME, context);
        } else {
            fire(UniIslFsmEvent._DISCOVERY_CHOICE_MOVED, context);
        }
    }

    public void handleMoved(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        if (!islReference.isIncomplete()) {
            emitIslMove(context);
        } else {
            log.debug("Do not emit ISL move for incomplete ISL reference {}", islReference);
        }
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

    @Value
    @Builder
    public static class UniIslFsmContext {
        private final IUniIslCarrier output;

        private Isl history;
        private IslInfoData discoveryEvent;

        public static UniIslFsmContextBuilder builder(IUniIslCarrier carrier) {
            return new UniIslFsmContextBuilder()
                    .output(carrier);
        }
    }

    public enum UniIslFsmEvent {
        NEXT, ACTIVATE,

        PHYSICAL_DOWN,
        DISCOVERY, FAIL,
        BFD_UP, BFD_DOWN, BFD_KILL,

        _DISCOVERY_CHOICE_SAME, _DISCOVERY_CHOICE_MOVED
    }

    public enum UniIslFsmState {
        INIT,
        UNKNOWN,

        DISCOVERY_CHOICE,

        UP, DOWN,
        BFD
    }
}
