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

package org.openkilda.wfm.topology.network.storm.bolt.history.command;

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.history.model.PortHistoryAction;
import org.openkilda.wfm.share.history.model.PortHistoryData;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.storm.bolt.history.HistoryHandler;

import lombok.Getter;

import java.time.Instant;

@Getter
public class PortHistoryCommand extends HistoryCommand {
    private final int portNumber;
    private final PortHistoryAction action;
    private final Instant time;

    public PortHistoryCommand(SwitchId switchId, int portNumber, PortHistoryAction action, Instant time) {
        super(switchId);
        this.portNumber = portNumber;
        this.action = action;
        this.time = time;
    }

    @Override
    public void apply(HistoryHandler handler) {
        PortHistoryData portData = PortHistoryData.builder()
                .endpoint(Endpoint.of(getSwitchId(), getPortNumber()))
                .action(action)
                .time(time)
                .build();
        handler.savePortStatusChangedEvent(portData);
    }
}
