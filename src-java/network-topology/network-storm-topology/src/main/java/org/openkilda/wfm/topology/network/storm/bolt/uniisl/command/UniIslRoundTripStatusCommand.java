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

package org.openkilda.wfm.topology.network.storm.bolt.uniisl.command;

import org.openkilda.wfm.topology.network.model.RoundTripStatus;
import org.openkilda.wfm.topology.network.storm.bolt.uniisl.UniIslHandler;

public class UniIslRoundTripStatusCommand extends UniIslCommand {
    private final RoundTripStatus status;

    public UniIslRoundTripStatusCommand(RoundTripStatus status) {
        super(status.getEndpoint());
        this.status = status;
    }

    @Override
    public void apply(UniIslHandler handler) {
        handler.processRoundTripStatus(status);
    }
}
