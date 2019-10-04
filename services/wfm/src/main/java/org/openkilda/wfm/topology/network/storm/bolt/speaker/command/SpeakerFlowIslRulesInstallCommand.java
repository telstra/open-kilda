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

import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerFlowWorker;

public class SpeakerFlowIslRulesInstallCommand extends SpeakerFlowWorkerCommand {
    Endpoint source;
    Endpoint destination;
    
    public SpeakerFlowIslRulesInstallCommand(String key, Endpoint source, Endpoint destination) {
        super(key);
        this.source = source;
        this.destination = destination;
    }

    @Override
    public void apply(SpeakerFlowWorker handler) {
        handler.processSetupIslDefaultRulesRequest(getKey(), source, destination);
    }

    public void timeout(SpeakerFlowWorker handler) {
        handler.timeoutIslRuleRequest(getKey(), source, destination);
    }
}
