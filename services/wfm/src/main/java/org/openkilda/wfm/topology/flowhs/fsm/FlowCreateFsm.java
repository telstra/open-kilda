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

package org.openkilda.wfm.topology.flowhs.fsm;

import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.State;

import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

public class FlowCreateFsm extends AbstractStateMachine<FlowCreateFsm, State, Event, Object> {

    static FlowCreateFsm newInstance() {
        StateMachineBuilder<FlowCreateFsm, State, Event, Object> builder =
                StateMachineBuilderFactory.create(FlowCreateFsm.class, State.class, Event.class, Object.class);

        builder.transitions()
                .from(State.Initialized)
                .toAmong(State.FlowValidated, State.FinishedWithError)
                .onEach(Event.Next, Event.Error);

        builder.transitions()
                .from(State.FlowValidated)
                .toAmong(State.ResourcesAllocated, State.ResourcesDeAllocated, State.FinishedWithError)
                .onEach(Event.Next, Event.Timeout, Event.Error);

        builder.externalTransition()
                .from(State.ResourcesAllocated)
                .to(State.InstallingNonIngressRules)
                .on(Event.Next);

        builder.internalTransition()
                .within(State.InstallingNonIngressRules)
                .on(Event.RuleInstalled);
        builder.transitions()
                .from(State.InstallingNonIngressRules)
                .toAmong(State.ValidatingNonIngressRules, State.RemovingRules, State.RemovingRules)
                .onEach(Event.Next, Event.Timeout, Event.Error);

        builder.internalTransition()
                .within(State.ValidatingNonIngressRules)
                .on(Event.RuleValidated);
        builder.transitions()
                .from(State.ValidatingNonIngressRules)
                .toAmong(State.InstallingIngressRules, State.RemovingRules, State.InstallingNonIngressRules)
                .onEach(Event.Next, Event.Timeout, Event.Error);

        builder.internalTransition()
                .within(State.InstallingIngressRules)
                .on(Event.RuleInstalled);
        builder.transitions()
                .from(State.InstallingIngressRules)
                .toAmong(State.ValidatingIngressRules, State.RemovingRules, State.RemovingRules)
                .onEach(Event.Next, Event.Timeout, Event.Error);

        builder.internalTransition()
                .within(State.ValidatingIngressRules)
                .on(Event.RuleValidated);
        builder.transitions()
                .from(State.ValidatingIngressRules)
                .toAmong(State.Finished, State.RemovingRules, State.RemovingRules)
                .onEach(Event.Next, Event.Timeout, Event.Error);

        builder.internalTransition()
                .within(State.RemovingRules)
                .on(Event.RuleDeleted);
        builder.transitions()
                .from(State.RemovingRules)
                .toAmong(State.ValidatingRemovedRules, State.NonDeletedRulesStored, State.NonDeletedRulesStored)
                .onEach(Event.Next, Event.Timeout, Event.Error);

        builder.internalTransition()
                .within(State.ValidatingRemovedRules)
                .on(Event.RuleDeletedConfirmation);
        builder.transitions()
                .from(State.ValidatingRemovedRules)
                .toAmong(State.ResourcesDeAllocated, State.NonDeletedRulesStored, State.NonDeletedRulesStored)
                .onEach(Event.Next, Event.Timeout, Event.Error);

        builder.transition()
                .from(State.NonDeletedRulesStored)
                .to(State.ResourcesDeAllocated)
                .on(Event.Next);

        builder.transition()
                .from(State.ResourcesDeAllocated)
                .toFinal(State.FinishedWithError)
                .on(Event.Next);

        return builder.newStateMachine(State.Initialized);
    }

    enum State {
        Initialized,
        FlowValidated,
        ResourcesAllocated,
        InstallingNonIngressRules,
        ValidatingNonIngressRules,
        InstallingIngressRules,
        ValidatingIngressRules,
        Finished,

        RemovingRules,
        ValidatingRemovedRules,
        ResourcesDeAllocated,
        NonDeletedRulesStored,
        FinishedWithError,
    }

    enum Event {
        Next,

        RuleInstalled,
        RuleValidated,
        RuleDeleted,
        RuleDeletedConfirmation,

        Timeout,
        Error
    }
}
