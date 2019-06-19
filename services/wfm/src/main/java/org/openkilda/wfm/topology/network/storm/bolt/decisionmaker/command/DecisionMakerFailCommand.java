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

package org.openkilda.wfm.topology.network.storm.bolt.decisionmaker.command;

import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.storm.bolt.decisionmaker.DecisionMakerHandler;

public class DecisionMakerFailCommand extends DecisionMakerCommand {
    private final long packetId;

    public DecisionMakerFailCommand(Endpoint endpoint, long packetId) {
        super(endpoint);
        this.packetId = packetId;
    }

    @Override
    public void apply(DecisionMakerHandler handler) {
        handler.processFailed(getEndpoint(), packetId);
    }
}
