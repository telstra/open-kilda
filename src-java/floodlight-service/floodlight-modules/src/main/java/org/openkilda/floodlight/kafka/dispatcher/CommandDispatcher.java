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

package org.openkilda.floodlight.kafka.dispatcher;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.messaging.command.CommandData;

import java.util.Optional;

public abstract class CommandDispatcher<T extends CommandData> {
    /**
     * Produce command to handle payload if this dispatcher is capable to produce command for this kind of payload.
     *
     * @param context command context
     * @param payload input command payload
     * @return produced command
     */
    public Optional<Command> dispatch(CommandContext context, CommandData payload) {
        if (!checkAcceptability(payload)) {
            return Optional.empty();
        }

        return Optional.of(makeCommand(context, unpack(payload)));
    }

    protected abstract boolean checkAcceptability(CommandData payload);

    protected abstract T unpack(CommandData payload);

    protected abstract Command makeCommand(CommandContext context, T data);
}
