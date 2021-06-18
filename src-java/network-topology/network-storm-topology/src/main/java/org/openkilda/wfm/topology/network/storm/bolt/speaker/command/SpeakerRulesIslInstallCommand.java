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
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRulesWorker;

public class SpeakerRulesIslInstallCommand extends SpeakerRulesWorkerCommand {
    Endpoint source;
    Endpoint destination;
    boolean multitableMode;
    boolean server42IslRtt;
    Integer server42Port;

    public SpeakerRulesIslInstallCommand(String key, Endpoint source, Endpoint destination, boolean multitableMode,
                                         boolean server42IslRtt, Integer server42Port) {
        super(key);
        this.source = source;
        this.destination = destination;
        this.multitableMode = multitableMode;
        this.server42IslRtt = server42IslRtt;
        this.server42Port = server42Port;
    }

    @Override
    public void apply(SpeakerRulesWorker handler) {
        handler.processSetupIslRulesRequest(getKey(), source, destination, multitableMode, server42IslRtt,
                server42Port);
    }

    public void timeout(SpeakerRulesWorker handler) {
        handler.timeoutIslRuleRequest(getKey(), source, destination);
    }
}
