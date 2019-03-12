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

package org.openkilda.wfm.share.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.openkilda.wfm.share.utils.AbstractBaseFsmTest.Fsm.Event;
import org.openkilda.wfm.share.utils.AbstractBaseFsmTest.Fsm.State;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;


@RunWith(MockitoJUnitRunner.class)
public class AbstractBaseFsmTest {

    @Test
    public void happyPath() {

        Fsm fsm = Fsm.builder.newStateMachine(State.START);
        fsm.fire(Event.ERROR);
        assertThat("Invalid state after exception.", fsm.getCurrentState(), is(State.START));
        fsm.fire(Event.NEXT);
        fsm.fire(Event.NEXT);
        assertThat("Invalid state after exception.", fsm.getCurrentState(), is(State.FINISH));
    }


    static class Fsm extends AbstractBaseFsm<Fsm, Fsm.State, Fsm.Event, Object> {

        public static final StateMachineBuilder<Fsm, State, Event, Object> builder;

        static {
            builder = StateMachineBuilderFactory.create(Fsm.class, State.class, Event.class, Object.class);

            builder.transition()
                    .from(State.START).to(State.MIDDLE).on(Event.ERROR)
                    .callMethod("raiseException");

            builder.transition()
                    .from(State.START).to(State.MIDDLE).on(Event.NEXT);

            builder.transition()
                    .from(State.MIDDLE).to(State.FINISH).on(Event.NEXT);
        }

        public void raiseException(State from, State to, Event event, Object context) {
            int x = 1000 / 0;
        }

        public enum Event {
            NEXT, ERROR
        }

        public enum State {
            START, MIDDLE, FINISH
        }
    }
}
