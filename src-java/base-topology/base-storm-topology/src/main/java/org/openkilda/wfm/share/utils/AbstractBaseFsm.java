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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.squirrelframework.foundation.fsm.StateMachine;
import org.squirrelframework.foundation.fsm.StateMachineStatus;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;


public abstract class AbstractBaseFsm<T extends StateMachine<T, S, E, C>, S, E, C>
        extends AbstractStateMachine<T, S, E, C> {

    protected transient Logger log = makeLog();

    @Override
    protected void afterTransitionCausedException(S fromState, S toState, E event, C context) {
        Throwable exception = getLastException().getTargetException();

        if (exception == null) {
            exception = getLastException();
        }

        log.error("Transition from \"{}\" to \"{}\" on \"{}\" with context \"{}\" caused exception.",
                fromState, toState, event, context, exception);

        setStatus(StateMachineStatus.IDLE);
    }

    private Logger makeLog() {
        return LoggerFactory.getLogger(getClass());
    }

}
