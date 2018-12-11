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

import org.openkilda.model.Isl;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.service.IPortCarrier;

import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

public final class PortFsm extends AbstractStateMachine<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> {
    private final Endpoint endpoint;
    private final Isl history;

    private static final StateMachineBuilder<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                PortFsm.class, PortFsmState.class, PortFsmEvent.class, PortFsmContext.class,
                // extra parameters
                Endpoint.class, Isl.class);

        // INIT
        builder.transition()
                .from(PortFsmState.INIT).to(PortFsmState.OPERATIONAL).on(PortFsmEvent.ONLINE);
        builder.transition()
                .from(PortFsmState.INIT).to(PortFsmState.UNOPERATIONAL).on(PortFsmEvent.OFFLINE);
        builder.onExit(PortFsmState.INIT)
                .callMethod("setupUniIsl");

        // OPERATIONAL
        builder.transition()
                .from(PortFsmState.OPERATIONAL).to(PortFsmState.UNOPERATIONAL).on(PortFsmEvent.OFFLINE);
        builder.defineSequentialStatesOn(PortFsmState.OPERATIONAL,
                                         PortFsmState.UNKNOWN, PortFsmState.UP, PortFsmState.DOWN);

        // UNOPERATIONAL
        builder.transition()
                .from(PortFsmState.UNOPERATIONAL).to(PortFsmState.OPERATIONAL).on(PortFsmEvent.ONLINE);

        // UNKNOWN
        builder.transition()
                .from(PortFsmState.UNKNOWN).to(PortFsmState.UP).on(PortFsmEvent.PORT_UP);
        builder.transition()
                .from(PortFsmState.UNKNOWN).to(PortFsmState.DOWN).on(PortFsmEvent.PORT_DOWN);

        // UP
        builder.transition()
                .from(PortFsmState.UP).to(PortFsmState.DOWN).on(PortFsmEvent.PORT_DOWN);
        builder.onEntry(PortFsmState.UP)
                .callMethod("enableDiscoveryPoll");

        // DOWN
        builder.transition()
                .from(PortFsmState.DOWN).to(PortFsmState.UP).on(PortFsmEvent.PORT_UP);
        builder.onEntry(PortFsmState.DOWN)
                .callMethod("downEnter");
    }

    public static FsmExecutor<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> makeExecutor() {
        return new FsmExecutor<>(PortFsmEvent.NEXT);
    }

    public static PortFsm create(Endpoint endpoint, Isl history) {
        return builder.newStateMachine(PortFsmState.INIT, endpoint, history);
    }

    private PortFsm(Endpoint endpoint, Isl history) {
        this.endpoint = endpoint;
        this.history = history;
    }

    // -- FSM actions --

    private void setupUniIsl(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().setupUniIslHandler(endpoint, history);
    }

    private void enableDiscoveryPoll(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        context.getOutput().enableDiscoveryPoll(endpoint);
    }

    private void downEnter(PortFsmState from, PortFsmState to, PortFsmEvent event, PortFsmContext context) {
        IPortCarrier output = context.getOutput();
        output.disableDiscoveryPoll(endpoint);
        output.notifyPortPhysicalDown(endpoint);
    }

    // -- private/service methods --
}
