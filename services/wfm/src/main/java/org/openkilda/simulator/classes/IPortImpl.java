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

package org.openkilda.simulator.classes;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.simulator.interfaces.IPort;
import org.openkilda.simulator.messages.simulator.command.PortModMessage;

import org.projectfloodlight.openflow.types.DatapathId;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

public class IPortImpl implements IPort {
    private static final int MAX_SMALL = 50;
    private static final int MAX_LARGE = 10000;

    private Random rand = new Random();
    private int number;
    private long rxPackets = 0;
    private long txPackets = 0;
    private long rxBytes = 0;
    private long txBytes = 0;
    private long rxDropped = 0;
    private long txDropped = 0;
    private long rxErrors = 0;
    private long txErrors = 0;
    private long rxFrameErr = 0;
    private long rxOverErr = 0;
    private long rxCrcErr = 0;
    private long collisions = 0;
    private boolean isActive = true;
    private boolean isForwarding = true;
    private int latency = 0;
    private DatapathId peerSwitch;
    private int peerPortNum = -1;
    private ISwitchImpl sw;

    public IPortImpl(ISwitchImpl sw, PortStateType state, int portNumber) throws SimulatorException {
        setSw(sw);
        if (state == PortStateType.UP) {
            enable();
        } else if (state == PortStateType.DOWN) {
            disable();
        } else {
            throw new SimulatorException(String.format("Unknown port state %s", state.toString()));
        }
        number = portNumber;
    }

    /**
     * Return.
     * @return info message
     */
    public InfoMessage makePorChangetMessage() {
        PortChangeType type = isActive ? PortChangeType.UP : PortChangeType.DOWN;

        PortInfoData data = new PortInfoData(
                new SwitchId(sw.getDpid().toString()),
                number,
                type
        );
        return new InfoMessage(data, Instant.now().toEpochMilli(), UUID.randomUUID().toString(), null, null);
    }

    @Override
    public void enable() {
        this.isActive = true;
        this.isForwarding = true;
    }

    @Override
    public void disable() {
        this.isActive = false;
        this.isForwarding = false;
    }

    @Override
    public void block() {
        this.isForwarding = false;
    }

    @Override
    public void unblock() {
        this.isForwarding = true;
    }

    public void modPort(PortModMessage message) {
        isActive = message.isActive();
        isForwarding = message.isForwarding();
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public boolean isForwarding() {
        return isForwarding;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @Override
    public void setLatency(int latency) {
        this.latency = latency;
    }

    @Override
    public int getLatency() {
        return latency;
    }

    @Override
    public String getPeerSwitch() {
        return peerSwitch.toString();
    }

    @Override
    public void setPeerSwitch(String peerSwitch) {
        this.peerSwitch = DatapathId.of(peerSwitch);
    }

    @Override
    public void setPeerPortNum(int peerPortNum) {
        this.peerPortNum = peerPortNum;
    }

    @Override
    public int getPeerPortNum() {
        return peerPortNum;
    }

    @Override
    public void setIsl(DatapathId peerSwitch, int peerPortNum) {
        this.peerSwitch = peerSwitch;
        this.peerPortNum = peerPortNum;
    }

    public ISwitchImpl getSw() {
        return sw;
    }

    public void setSw(ISwitchImpl sw) {
        this.sw = sw;
    }

    @Override
    public boolean isActiveIsl() {
        return (peerSwitch != null && peerPortNum >= 0 && isActive && isForwarding);
    }

    @Override
    public PortStatsEntry getStats() {
        if (isForwarding && isActive) {
            this.rxPackets += rand.nextInt(MAX_LARGE);
            this.txPackets += rand.nextInt(MAX_LARGE);
            this.rxBytes += rand.nextInt(MAX_LARGE);
            this.txBytes += rand.nextInt(MAX_LARGE);
            this.rxDropped += rand.nextInt(MAX_SMALL);
            this.txDropped += rand.nextInt(MAX_SMALL);
            this.rxErrors += rand.nextInt(MAX_SMALL);
            this.txErrors += rand.nextInt(MAX_SMALL);
            this.rxFrameErr += rand.nextInt(MAX_SMALL);
            this.rxOverErr += rand.nextInt(MAX_SMALL);
            this.rxCrcErr += rand.nextInt(MAX_SMALL);
            this.collisions += rand.nextInt(MAX_SMALL);
        }

        return new PortStatsEntry(this.number, this.rxPackets, this.txPackets, this.rxBytes, this.txBytes,
                this.rxDropped, this.txDropped, this.rxErrors, this.txErrors, this.rxFrameErr, this.rxOverErr,
                this.rxCrcErr, this.collisions);
    }
}
