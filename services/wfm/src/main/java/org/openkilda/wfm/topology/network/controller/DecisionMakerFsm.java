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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.DecisionMakerFsm.DecisionMakerFsmContext;
import org.openkilda.wfm.topology.network.controller.DecisionMakerFsm.DecisionMakerFsmEvent;
import org.openkilda.wfm.topology.network.controller.DecisionMakerFsm.DecisionMakerFsmState;
import org.openkilda.wfm.topology.network.service.IDecisionMakerCarrier;

import lombok.Builder;
import lombok.Data;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

public final class DecisionMakerFsm extends AbstractBaseFsm<DecisionMakerFsm,
        DecisionMakerFsmState,
        DecisionMakerFsmEvent,
        DecisionMakerFsmContext> {
    private static final StateMachineBuilder<DecisionMakerFsm, DecisionMakerFsmState, DecisionMakerFsmEvent,
            DecisionMakerFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                DecisionMakerFsm.class, DecisionMakerFsmState.class, DecisionMakerFsmEvent.class,
                DecisionMakerFsmContext.class,
                Endpoint.class, Long.class, Long.class);

        final String verifyAndTransit = "verifyAndTransit";

        // INIT
        builder.transition()
                .from(DecisionMakerFsmState.INIT).to(DecisionMakerFsmState.DISCOVERED)
                .on(DecisionMakerFsmEvent.DISCOVERY);
        builder.transition()
                .from(DecisionMakerFsmState.INIT).to(DecisionMakerFsmState.UNSTABLE)
                .on(DecisionMakerFsmEvent.FAIL);

        // UNSTABLE
        builder.onEntry(DecisionMakerFsmState.UNSTABLE)
                .callMethod("saveFailTime");
        builder.internalTransition().within(DecisionMakerFsmState.UNSTABLE).on(DecisionMakerFsmEvent.DISCOVERY)
                .callMethod(verifyAndTransit);
        builder.internalTransition().within(DecisionMakerFsmState.UNSTABLE).on(DecisionMakerFsmEvent.FAIL)
                .callMethod(verifyAndTransit);
        builder.transition()
                .from(DecisionMakerFsmState.UNSTABLE).to(DecisionMakerFsmState.DISCOVERED)
                .on(DecisionMakerFsmEvent.VALID_DISCOVERY);
        builder.internalTransition().within(DecisionMakerFsmState.UNSTABLE).on(DecisionMakerFsmEvent.VALID_FAIL)
                .callMethod("tick");
        builder.internalTransition().within(DecisionMakerFsmState.UNSTABLE).on(DecisionMakerFsmEvent.TICK)
                .callMethod("tick");
        builder.transition()
                .from(DecisionMakerFsmState.UNSTABLE).to(DecisionMakerFsmState.FAILED)
                .on(DecisionMakerFsmEvent.FAIL_BY_TIMEOUT);

        // DISCOVERED
        builder.onEntry(DecisionMakerFsmState.DISCOVERED)
                .callMethod("emitDiscovery");
        builder.internalTransition()
                .within(DecisionMakerFsmState.DISCOVERED).on(DecisionMakerFsmEvent.FAIL)
                .callMethod(verifyAndTransit);
        builder.internalTransition().within(DecisionMakerFsmState.DISCOVERED).on(DecisionMakerFsmEvent.DISCOVERY)
                .callMethod(verifyAndTransit);
        builder.transition()
                .from(DecisionMakerFsmState.DISCOVERED).to(DecisionMakerFsmState.UNSTABLE)
                .on(DecisionMakerFsmEvent.VALID_FAIL);
        builder.internalTransition().within(DecisionMakerFsmState.DISCOVERED).on(DecisionMakerFsmEvent.VALID_DISCOVERY)
                .callMethod("emitDiscovery");

        // FAILED
        builder.onEntry(DecisionMakerFsmState.FAILED)
                .callMethod("emitFailed");
        builder.internalTransition()
                .within(DecisionMakerFsmState.FAILED).on(DecisionMakerFsmEvent.DISCOVERY)
                .callMethod(verifyAndTransit);
        builder.transition()
                .from(DecisionMakerFsmState.FAILED).to(DecisionMakerFsmState.DISCOVERED)
                .on(DecisionMakerFsmEvent.VALID_DISCOVERY);
    }

    private final Endpoint endpoint;
    private final Long failTimeout;
    private final Long awaitTime;
    private Long failTime;
    private Long lastProcessedPacketId;

    public DecisionMakerFsm(Endpoint endpoint, Long failTimeout, Long awaitTime) {
        this.endpoint = endpoint;
        this.failTimeout = failTimeout;
        this.awaitTime = awaitTime;
    }

    public static FsmExecutor<DecisionMakerFsm, DecisionMakerFsmState, DecisionMakerFsmEvent,
            DecisionMakerFsmContext> makeExecutor() {
        return new FsmExecutor<>(DecisionMakerFsmEvent.NEXT);
    }

    public static DecisionMakerFsm create(Endpoint endpoint, Long failTimeout, Long awaitTime) {
        return builder.newStateMachine(DecisionMakerFsmState.INIT, endpoint, failTimeout, awaitTime);
    }

    // -- FSM actions --

    public void verifyAndTransit(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                                 DecisionMakerFsmContext context) {
        boolean verification = lastProcessedPacketId == null;
        if (!verification) {
            verification = context.getPacketId() != null && lastProcessedPacketId < context.getPacketId();
        }

        if (verification) {
            fire(mapVerificationEvent(event), context);
        }
    }

    public void saveFailTime(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                             DecisionMakerFsmContext context) {
        saveLastProcessed(context);
        failTime = context.getCurrentTime() - awaitTime;
    }

    public void emitDiscovery(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                              DecisionMakerFsmContext context) {
        saveLastProcessed(context);
        context.getOutput().linkDiscovered(context.getDiscoveryEvent());
        failTime = null;
    }

    public void emitFailed(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                           DecisionMakerFsmContext context) {
        saveLastProcessed(context);
        context.getOutput().linkDestroyed(endpoint);
        failTime = null;
    }

    /**
     * Tick event process.
     */
    public void tick(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                     DecisionMakerFsmContext context) {
        saveLastProcessed(context);
        if (context.getCurrentTime() >= failTime + failTimeout) {
            fire(DecisionMakerFsmEvent.FAIL_BY_TIMEOUT, context);
        }
    }

    // -- private/service methods --

    private DecisionMakerFsmEvent mapVerificationEvent(DecisionMakerFsmEvent event) {
        DecisionMakerFsmEvent result;
        switch (event) {
            case FAIL:
                result = DecisionMakerFsmEvent.VALID_FAIL;
                break;
            case DISCOVERY:
                result = DecisionMakerFsmEvent.VALID_DISCOVERY;
                break;
            default:
                throw new IllegalArgumentException(String.format("Threre is no verification mapping for %s.%s",
                                                                 event.getClass().getName(), event));
        }
        return result;
    }

    private void saveLastProcessed(DecisionMakerFsmContext context) {
        if (context.getPacketId() != null) {
            lastProcessedPacketId = context.getPacketId();
        }
    }

    // -- service data types --

    @Data
    @Builder
    public static class DecisionMakerFsmContext {
        private final IDecisionMakerCarrier output;
        private final IslInfoData discoveryEvent;
        private final Long currentTime;
        private final Long packetId;
    }

    public enum DecisionMakerFsmEvent {
        NEXT,

        DISCOVERY, FAIL,
        VALID_DISCOVERY, VALID_FAIL,
        TICK,

        FAIL_BY_TIMEOUT
    }

    public enum DecisionMakerFsmState {
        INIT,

        DISCOVERED, FAILED, UNSTABLE,
    }
}
