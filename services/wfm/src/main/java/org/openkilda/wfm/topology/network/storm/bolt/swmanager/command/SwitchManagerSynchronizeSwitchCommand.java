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

package org.openkilda.wfm.topology.network.storm.bolt.swmanager.command;

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.SwitchManagerWorker;

public class SwitchManagerSynchronizeSwitchCommand extends SwitchManagerWorkerCommand {

    private final SwitchId switchId;

    public SwitchManagerSynchronizeSwitchCommand(String key, SwitchId switchId) {
        super(key);
        this.switchId = switchId;
    }

    @Override
    public void apply(SwitchManagerWorker handler) {
        handler.processSynchronizeSwitchRequest(getKey(), switchId);
    }

    @Override
    public void timeout(SwitchManagerWorker handler) {
        handler.timeoutSwitchManagerRequest(getKey(), switchId);
    }
}
