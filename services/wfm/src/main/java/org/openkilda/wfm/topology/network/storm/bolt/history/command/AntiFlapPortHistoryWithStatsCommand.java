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

import org.openkilda.wfm.share.history.model.PortHistoryData;
import org.openkilda.wfm.share.history.model.PortHistoryEvent;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.storm.bolt.history.HistoryHandler;

import java.time.Instant;

public class AntiFlapPortHistoryWithStatsCommand extends AntiFlapPortHistoryCommand {
    private final int upEventsCount;
    private final int downEventsCount;

    public AntiFlapPortHistoryWithStatsCommand(Endpoint endpoint, PortHistoryEvent event, Instant time,
                                               int upEventsCount, int downEventsCount) {
        super(endpoint, event, time);
        this.upEventsCount = upEventsCount;
        this.downEventsCount = downEventsCount;
    }

    @Override
    public void apply(HistoryHandler handler) {
        PortHistoryData portData = PortHistoryData.builder()
                .endpoint(Endpoint.of(getSwitchId(), getPortNumber()))
                .event(getEvent())
                .time(getTime())
                .upEventsCount(upEventsCount)
                .downEventsCount(downEventsCount)
                .build();
        handler.savePortStatusChangedEvent(portData);
    }
}
