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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.service.IDecisionMakerCarrier;

import lombok.Builder;
import lombok.Data;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

public final class DecisionMakerFsm extends AbstractStateMachine<DecisionMakerFsm,
        DecisionMakerFsm.DecisionMakerFsmState,
        DecisionMakerFsm.DecisionMakerFsmEvent,
        DecisionMakerFsm.DecisionMakerFsmContext> {
    private static final StateMachineBuilder<DecisionMakerFsm, DecisionMakerFsmState, DecisionMakerFsmEvent,
            DecisionMakerFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                DecisionMakerFsm.class, DecisionMakerFsmState.class, DecisionMakerFsmEvent.class,
                DecisionMakerFsmContext.class,
                Endpoint.class, Long.class, Long.class);

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
        builder.transition()
                .from(DecisionMakerFsmState.UNSTABLE).to(DecisionMakerFsmState.DISCOVERED)
                .on(DecisionMakerFsmEvent.DISCOVERY);
        builder.internalTransition().within(DecisionMakerFsmState.UNSTABLE).on(DecisionMakerFsmEvent.TICK)
                .callMethod("tick");
        builder.internalTransition().within(DecisionMakerFsmState.UNSTABLE).on(DecisionMakerFsmEvent.FAIL)
                .callMethod("tick");
        builder.transition()
                .from(DecisionMakerFsmState.UNSTABLE).to(DecisionMakerFsmState.FAILED)
                .on(DecisionMakerFsmEvent.FAIL_BY_TIMEOUT);

        // DISCOVERED
        builder.onEntry(DecisionMakerFsmState.DISCOVERED)
                .callMethod("emitDiscovery");
        builder.transition()
                .from(DecisionMakerFsmState.DISCOVERED).to(DecisionMakerFsmState.UNSTABLE)
                .on(DecisionMakerFsmEvent.FAIL);

        // FAILED
        builder.onEntry(DecisionMakerFsmState.FAILED)
                .callMethod("emitFailed");
        builder.transition()
                .from(DecisionMakerFsmState.FAILED).to(DecisionMakerFsmState.DISCOVERED)
                .on(DecisionMakerFsmEvent.DISCOVERY);

    }

    private final Endpoint endpoint;
    private final Long failTimeout;
    private final Long awaitTime;
    private Long failTime;

    private DecisionMakerFsm(Endpoint endpoint, Long failTimeout, Long awaitTime) {
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

    private void saveFailTime(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                              DecisionMakerFsmContext context) {
        failTime = context.getCurrentTime() - awaitTime;
    }

    private void emitDiscovery(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                               DecisionMakerFsmContext context) {
        context.getOutput().linkDiscovered(context.getDiscoveryEvent());
        failTime = null;
    }

    private void emitFailed(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                            DecisionMakerFsmContext context) {
        context.getOutput().linkDestroyed(endpoint);
        failTime = null;
    }

    private void tick(DecisionMakerFsmState from, DecisionMakerFsmState to, DecisionMakerFsmEvent event,
                      DecisionMakerFsmContext context) {
        if (context.getCurrentTime() >= failTime + failTimeout) {
            fire(DecisionMakerFsmEvent.FAIL_BY_TIMEOUT, context);
        }
    }

    @Data
    @Builder
    public static class DecisionMakerFsmContext {
        private final IDecisionMakerCarrier output;
        private final IslInfoData discoveryEvent;
        private final Long currentTime;
    }

    public enum DecisionMakerFsmEvent {
        NEXT,

        DISCOVERY, FAIL, TICK,

        FAIL_BY_TIMEOUT
    }

    public enum DecisionMakerFsmState {
        INIT,

        DISCOVERED, FAILED, UNSTABLE,
    }
}
