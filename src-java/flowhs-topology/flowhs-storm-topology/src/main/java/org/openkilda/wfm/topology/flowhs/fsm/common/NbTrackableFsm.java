/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import org.openkilda.messaging.Message;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.squirrelframework.foundation.fsm.StateMachine;

public abstract class NbTrackableFsm<T extends StateMachine<T, S, E, C>, S, E, C, R extends FlowGenericCarrier>
        extends FlowProcessingFsm<T, S, E, C, R> {

    private final R carrier;
    private final boolean allowNorthboundResponse;

    @Getter
    @Setter
    private Message operationResultMessage;

    public NbTrackableFsm(CommandContext commandContext, @NonNull R carrier) {
        this(commandContext, carrier, true);
    }

    public NbTrackableFsm(CommandContext commandContext, @NonNull R carrier, boolean allowNorthboundResponse) {
        super(commandContext);

        this.carrier = carrier;
        this.allowNorthboundResponse = allowNorthboundResponse;
    }

    @Override
    public R getCarrier() {
        return carrier;
    }

    public void sendNorthboundResponse(Message message) {
        if (allowNorthboundResponse) {
            carrier.sendNorthboundResponse(message);
        }
    }
}
