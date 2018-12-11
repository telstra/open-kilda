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

package org.openkilda.wfm.topology.discovery.controller;

import org.openkilda.model.Isl;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.facts.DiscoveryFacts;

import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

public class UniIslFsm extends AbstractStateMachine<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> {
    private final Endpoint endpoint;

    private DiscoveryFacts discoveryFacts;

    private static final StateMachineBuilder<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                UniIslFsm.class, UniIslFsmState.class, UniIslFsmEvent.class, UniIslFsmContext.class,
                // extra parameters
                Endpoint.class, Isl.class);

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
        builder.onEntry(UniIslFsmState.BFD)
                .callMethod("bfdEnter");
    }

    public static FsmExecutor<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> makeExecutor() {
        return new FsmExecutor<>(UniIslFsmEvent.NEXT);
    }

    public static UniIslFsm create(Endpoint endpoint, Isl history) {
        return builder.newStateMachine(UniIslFsmState.UNKNOWN, endpoint, history);
    }

    public UniIslFsm(Endpoint endpoint, Isl history) {
        this.endpoint = endpoint;
        if (history != null) {
            discoveryFacts = new DiscoveryFacts(history);
        } else {
            discoveryFacts = new DiscoveryFacts(new IslReference(endpoint));
        }
    }

    // -- FSM actions --

    private void makeDiscoveryChoice(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event,
                                     UniIslFsmContext context) {
        IslReference actualReference = IslReference.of(context.getDiscoveryEvent());
        if (discoveryFacts.getReference().equals(actualReference)) {
            fire(UniIslFsmEvent._DISCOVERY_CHOICE_SAME, context);
        } else {
            fire(UniIslFsmEvent._DISCOVERY_CHOICE_MOVED, context);
        }
    }

    private void handleMoved(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        emitIslMove(context);
    }

    private void upEnter(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        discoveryFacts = new DiscoveryFacts(context.getDiscoveryEvent());
        emitIslUp(context);
    }

    private void downEnter(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        emitIslDown(context, event == UniIslFsmEvent.PHYSICAL_DOWN);
    }

    private void bfdEnter(UniIslFsmState from, UniIslFsmState to, UniIslFsmEvent event, UniIslFsmContext context) {
        emitIslUp(context);
    }

    // -- private/service methods --

    private void emitIslUp(UniIslFsmContext context) {
        context.getOutput().notifyIslUp(endpoint, discoveryFacts);
    }

    private void emitIslDown(UniIslFsmContext context, boolean isPhysicalDown) {
        context.getOutput().notifyIslDown(endpoint, discoveryFacts.getReference(), isPhysicalDown);
    }

    private void emitIslMove(UniIslFsmContext context) {
        context.getOutput().notifyIslMove(endpoint, discoveryFacts.getReference());
    }
}
