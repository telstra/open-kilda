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

package org.openkilda.wfm.topology.discovery.storm.bolt.speaker.command;

import org.openkilda.wfm.topology.discovery.service.ISpeakerWorkerCarrier;
import org.openkilda.wfm.topology.discovery.storm.ICommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.speaker.SpeakerWorker;

import lombok.Getter;

public abstract class SpeakerWorkerCommand implements ICommand<SpeakerWorker, ISpeakerWorkerCarrier> {
    @Getter
    private final String key;

    public SpeakerWorkerCommand(String key) {
        this.key = key;
    }

    @Override
    public void apply(SpeakerWorker service, ISpeakerWorkerCarrier carrier) {
        this.apply(service);
    }

    public abstract void apply(SpeakerWorker service);
}
