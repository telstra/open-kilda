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

package org.openkilda.wfm.share.utils;

import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

public class FsmExecutor<T extends AbstractStateMachine<T, S, E, C>, S, E, C> {
    private final E next;

    public FsmExecutor(E next) {
        this.next = next;
    }

    /**
     * Fire "next" event into FSM until it stops switch state.
     *
     * <p>In other words it force FSM to process all intermediate states and stop in "major" state.</p>
     */
    public T fire(T fsm, E event, C context) {
        fsm.fire(event, context);
        skipIntermediateStates(fsm, context);
        return fsm;
    }

    public T fire(T fsm, E event) {
        fire(fsm, event, null);
        return fsm;
    }

    private void skipIntermediateStates(T fsm, C context) {
        S originState = null;
        S finalState = fsm.getCurrentState();
        while (!fsm.isTerminated() && originState != finalState) {
            originState = finalState;
            fsm.fire(next, context);
            finalState = fsm.getCurrentState();
        }
    }
}
