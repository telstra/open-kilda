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

package org.openkilda.wfm.topology.network.storm.bolt.speaker.command;

import org.openkilda.messaging.info.discovery.RemoveIslDefaultRulesResult;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerFlowWorker;

public class SpeakerFlowIslRulesRemovedCommand extends SpeakerFlowWorkerCommand {
    RemoveIslDefaultRulesResult payload;

    public SpeakerFlowIslRulesRemovedCommand(String key, RemoveIslDefaultRulesResult payload) {
        super(key);
        this.payload = payload;
    }

    @Override
    public void apply(SpeakerFlowWorker handler) {
        handler.processRemoveIslDefaultRulesResponse(getKey(), payload);
    }

    @Override
    public void timeout(SpeakerFlowWorker handler) {

    }
}
