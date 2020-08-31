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

package org.openkilda.floodlight.kafka.dispatcher;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.statistics.StatsCommand;
import org.openkilda.messaging.command.BroadcastWrapper;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.stats.StatsRequest;

// FIXME(surabujin): make nested dispatching... if we are going to keep  this approach
public class BroadcastStatsRequestDispatcher extends CommandDispatcher<BroadcastWrapper> {
    @Override
    protected boolean checkAcceptability(CommandData payload) {
        return payload instanceof BroadcastWrapper
                && ((BroadcastWrapper) payload).getPayload() instanceof StatsRequest;
    }

    @Override
    protected BroadcastWrapper unpack(CommandData payload) {
        return (BroadcastWrapper) payload;
    }

    @Override
    protected Command makeCommand(CommandContext context, BroadcastWrapper wrapper) {
        return new StatsCommand(context, wrapper.getScope());
    }
}
