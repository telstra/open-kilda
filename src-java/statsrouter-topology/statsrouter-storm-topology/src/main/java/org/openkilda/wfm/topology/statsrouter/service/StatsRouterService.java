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

package org.openkilda.wfm.topology.statsrouter.service;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.messaging.command.switches.ListSwitchRequest;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.ListSwitchResponse;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public final class StatsRouterService {
    private final Clock clock;
    private final MessageSender messageSender;
    private final int timeout;
    private Map<String, ConnectedInfo> connectedToStats = new HashMap<>();

    public StatsRouterService(int timeout, MessageSender messageSender) {
        this(timeout, messageSender, Clock.systemDefaultZone());
    }

    /**
     * This constructor is used only for testing.
     *
     * @param timeout the timeout in seconds.
     * @param messageSender the messageSender.
     * @param clock the clock.
     */
    StatsRouterService(int timeout, MessageSender messageSender, Clock clock) {
        this.timeout = timeout;
        this.messageSender = messageSender;
        this.clock = clock;
    }

    /**
     * Takes the stats request and sends requests to FL Management and FL Statistics.
     *
     * @param statsRequest the incoming stats request.
     */
    public void handleStatsRequest(CommandMessage statsRequest) {
        List<SwitchId> excluded = connectedToStats.values().stream()
                .flatMap(it -> it.switchIds.stream())
                .collect(Collectors.toList());
        CommandMessage mgmtRequest = new CommandMessage(new StatsRequest(excluded), statsRequest.getTimestamp(),
                statsRequest.getCorrelationId());
        messageSender.sendToMgmt(mgmtRequest);
        log.debug("Stats request has been sent to management floodlight: {}", mgmtRequest);
        messageSender.sendToStats(statsRequest);
        log.debug("Stats request has been sent to statistics floodlight: {}", statsRequest);
    }

    /**
     * Updates internal map with switches connected to FL Statistics.
     *
     * @param listSwitches message from FL Statistics.
     */
    public void handleListSwitchesResponse(InfoMessage listSwitches) {
        InfoData data = listSwitches.getData();
        if (data instanceof ListSwitchResponse) {
            log.debug("Process response with list of switches connected to statistics floodlight: {}", data);
            ListSwitchResponse response = (ListSwitchResponse) data;
            ConnectedInfo connectedInfo = new ConnectedInfo(
                    response.getSwitchIds(),
                    response.getControllerId(),
                    LocalDateTime.now(clock));
            connectedToStats.put(connectedInfo.controllerId, connectedInfo);
        } else {
            log.warn("Unknown message data {}", data);
        }
    }

    /**
     * Do periodic work.
     * Delete outdated FL statistics instances.
     * Send request to FL statistics.
     */
    public void handleTick() {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(timeout, ChronoUnit.SECONDS);
        connectedToStats.values().stream()
                .filter(info -> info.time.isBefore(threshold))
                .map(info -> info.controllerId)
                .collect(Collectors.toList())
                .forEach(connectedToStats::remove);
        Message requestConnected =
                new CommandMessage(new ListSwitchRequest(), System.currentTimeMillis(), UUID.randomUUID().toString());
        messageSender.sendToStats(requestConnected);
        log.debug("Request for getting a list of switches connected to statistics floodlight has been sent: {}",
                requestConnected);
    }

    private final class ConnectedInfo {
        public final List<SwitchId> switchIds;
        public final String controllerId;
        public final LocalDateTime time;

        private ConnectedInfo(List<SwitchId> switchIds, String controllerId, LocalDateTime time) {
            this.switchIds = switchIds;
            this.controllerId = controllerId;
            this.time = time;
        }
    }
}
