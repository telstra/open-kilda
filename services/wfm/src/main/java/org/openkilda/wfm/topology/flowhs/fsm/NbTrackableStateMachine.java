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

import org.openkilda.messaging.Message;
import org.openkilda.wfm.CommandContext;

import lombok.Getter;
import org.squirrelframework.foundation.fsm.StateMachine;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

@Getter
public abstract class NbTrackableStateMachine<T extends StateMachine<T, S, E, C>, S, E, C>
        extends AbstractStateMachine<T, S, E, C> {

    private final CommandContext commandContext;

    public NbTrackableStateMachine(CommandContext commandContext) {
        this.commandContext = commandContext;
    }

    public abstract void fireNext(C context);

    public abstract void fireError();

    public abstract void sendResponse(Message message);

}
