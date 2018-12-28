/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.event.service;

import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.model.SwitchId;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The service throttling Port UP / DOWN flapping event.
 *
 * <p>For using it you need to:
 * - pass port events (only UP or DOWN) via processEvent function.
 * - periodically call getPortInfos function.
 *
 * <h2>Details of idea.</h2>
 *
 * <p>Each port has states:
 * <br>- Nothing (not presented in portStats map)
 * <br>- WarmingUp (PortState.coolingState == false)
 * <br>- CoolingDown (PortState.coolingState == true)
 *
 * <p>Initially, the port has state Nothing.
 * <br>If the service receives "Port Up" in Nothing state it must emit "Port Up" without state change.
 * <br>If the service receives "Port Down" in Nothing state it changes state to WarmingUp.
 * <br>If the service does not receive "Port Up" after delay_seconds_min, it emits "Port Down" event and goes to
 * CoolingDown state.
 * <br>If In CoolingDown state the service receives event, it calculates current port state and restarts
 * seconds_cool_down timer.
 * <br>After seconds_cool_down of not receiving any events, state changes to Nothing. If the current port state is Up
 * the service emit "Port Up" event.
 *
 * <p>In case with WarmingUp state, when delays between every "Port Down" / "Port Up" were less then delay_seconds_min
 * and the last "Port Down" / "Port Up" event was during last delay_seconds_min period of delay_seconds_warm_up,
 * the service emit "Port Down" event and go to CoolingDown state.
 */
@Slf4j
public class PortEventThrottlingService {
    private final Clock clock;
    private final int minDelay;
    private final int warmUpDelay;
    private final int coolDownDelay;
    private final Map<SwitchPort, PortState> portStats = new HashMap<>();

    /**
     * The constructor for PortEventThrottlingService.
     *
     * @param minDelay the min delay between Port Down / Up change in seconds.
     * @param warmUpDelay the delay for warm up window in seconds.
     * @param coolDownDelay the delay for cool down window in seconds.
     */
    public PortEventThrottlingService(int minDelay, int warmUpDelay, int coolDownDelay) {
        this(minDelay, warmUpDelay, coolDownDelay, Clock.systemDefaultZone());
    }

    /**
     * This constructor is used only for testing.
     *
     * @param minDelay the min delay between Port Down / Up change.
     * @param warmUpDelay the delay for warm up window.
     * @param coolDownDelay the delay for cool down window.
     * @param clock the clock.
     */
    PortEventThrottlingService(int minDelay, int warmUpDelay, int coolDownDelay, Clock clock) {
        this.minDelay = minDelay;
        this.warmUpDelay = warmUpDelay;
        this.coolDownDelay = coolDownDelay;
        this.clock = clock;
    }

    /**
     * Processing an event of PortInfoData.
     * Store it internally for Throttling or return true if Port in UP state and no case is opened for the Port.
     *
     * @param data the PortInfoData object.
     * @return true if the event can be sent further.
     */
    public boolean processEvent(PortInfoData data, String correlationId) {
        SwitchPort switchPort = new SwitchPort(data.getSwitchId(), data.getPortNo());
        PortState portState = portStats.get(switchPort);
        if (data.getState() == PortChangeType.UP && portState == null) {
            log.info("Update port {}-{} to UP immediately", data.getSwitchId(), data.getPortNo());
            return true;
        }
        if (portState == null) {
            portState = new PortState();
            portStats.put(switchPort, portState);
            LocalDateTime now = getNow();
            portState.initialCorrelationId = correlationId;
            portState.firstEventTime = now;
            portState.lastEventTime = now;
            portState.portIsUp = false;
            portState.coolingState = false;
            log.info("First Port DOWN state received for {}-{} change port to WarmingUp state",
                    data.getSwitchId(), data.getPortNo());
        } else {
            portState.portIsUp = data.getState() == PortChangeType.UP;
            portState.lastEventTime = getNow();
            log.info("Collecting state for port {}-{}. Original correlationId: {}.",
                    data.getSwitchId(), data.getPortNo(), portState.initialCorrelationId);
        }
        return false;
    }

    private LocalDateTime getNow() {
        return LocalDateTime.now(clock);
    }

    /**
     * Check current statuses, update it if needed and generate list of PortInfo.
     *
     * @return list of PortInfo must be sent.
     */
    public List<PortInfoContainer> getPortInfos() {
        PortStatWorkflow portStatWorkflow = new PortStatWorkflow();
        portStats.forEach(portStatWorkflow::processPortStat);
        portStatWorkflow.switchPortsToDelete.forEach(portStats::remove);
        return portStatWorkflow.result;
    }

    private class PortStatWorkflow {
        List<PortInfoContainer> result = new ArrayList<>();
        LocalDateTime now = getNow();
        List<SwitchPort> switchPortsToDelete = new ArrayList<>();

        void processPortStat(SwitchPort switchPort, PortState portState) {
            if (portState.coolingState) {
                processCoolingDown(switchPort, portState);
            } else {
                processWarmingUp(switchPort, portState);
            }
        }

        void processCoolingDown(SwitchPort switchPort, PortState portState) {
            if (portState.isCoolDownEnded(now)) {
                // end of CoolDown period
                switchPortsToDelete.add(switchPort);
                // send PortUp event if port is UP
                if (portState.portIsUp) {
                    result.add(getPortInfoContainer(switchPort, portState, true));
                }
                log.info("CoolingDown period ends for port {}-{} and status is: {}. Original correlationId: {}.",
                        switchPort.switchId, switchPort.port, portState.portIsUp ? "UP" : "DOWN",
                        portState.initialCorrelationId);
            }
        }

        void processWarmingUp(SwitchPort switchPort, PortState portState) {
            if (!portState.portIsUp && portState.isMinDelayOver(now)) {
                result.add(getPortInfoContainer(switchPort, portState, false));
                portState.lastEventTime = now;
                portState.coolingState = true;
                log.info("Port {}-{} was in DOWN state for too long. Change state to CoolingDown. "
                                + "Original correlationId: {}.", switchPort.switchId, switchPort.port,
                        portState.initialCorrelationId);
            } else if (portState.isWarmUpEnded(now)) {
                // end of WarmUp period
                if (!portState.portIsUp || portState.isAnyEventOnEndOfWarmUp()) {
                    // port is down after WarmUp period or
                    // we have an update on the end of WarmUp period
                    result.add(getPortInfoContainer(switchPort, portState, false));
                    portState.lastEventTime = now;
                    portState.coolingState = true;
                    log.info("WarmingUp period ends for port {}-{}. The port goes to CoolingDown state. "
                                    + "Original correlationId: {}.", switchPort.switchId, switchPort.port,
                            portState.initialCorrelationId);
                } else {
                    switchPortsToDelete.add(switchPort);
                    log.info("WarmingUp period ends for port {}-{}. Original correlationId: {}.",
                            switchPort.switchId, switchPort.port, portState.initialCorrelationId);
                }
            }
        }

        private PortInfoContainer getPortInfoContainer(SwitchPort switchPort, PortState portState, boolean isUp) {
            PortInfoData portInfoData =
                    new PortInfoData(switchPort.switchId, switchPort.port,
                            isUp ? PortChangeType.UP : PortChangeType.DOWN);
            log.info("Sending status: {} for port {}-{}.", portState.portIsUp ? "UP" : "DOWN", switchPort.switchId,
                    switchPort.port);
            return new PortInfoContainer(portInfoData, portState.initialCorrelationId);
        }
    }

    @Value
    private static class SwitchPort {
        SwitchId switchId;
        int port;
    }

    private class PortState {
        String initialCorrelationId;
        LocalDateTime firstEventTime;
        LocalDateTime lastEventTime;
        boolean portIsUp;
        boolean coolingState;

        boolean isCoolDownEnded(LocalDateTime now) {
            return lastEventTime.plusSeconds(coolDownDelay).isBefore(now);
        }

        boolean isMinDelayOver(LocalDateTime now) {
            return lastEventTime.plusSeconds(minDelay).isBefore(now);
        }

        boolean isWarmUpEnded(LocalDateTime now) {
            return firstEventTime.plusSeconds(warmUpDelay).isBefore(now);
        }

        boolean isAnyEventOnEndOfWarmUp() {
            return lastEventTime.isAfter(firstEventTime.plusSeconds((long) warmUpDelay - minDelay));
        }
    }

    public static final class PortInfoContainer {
        public final PortInfoData portInfoData;
        public final String correlationId;

        private PortInfoContainer(PortInfoData portInfoData, String correlationId) {
            this.portInfoData = portInfoData;
            this.correlationId = correlationId;
        }
    }
}
