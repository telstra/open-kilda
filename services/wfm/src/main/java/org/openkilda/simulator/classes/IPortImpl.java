package org.openkilda.simulator.classes;

import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.simulator.classes.PortStateType;
import org.openkilda.simulator.classes.SimulatorException;
import org.openkilda.simulator.interfaces.IPort;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Random;

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

    public IPortImpl(PortStateType state, int portNumber) throws SimulatorException {
        if (state == PortStateType.UP) {
            enable();
        } else if (state == PortStateType.DOWN) {
            disable();
        } else {
            throw new SimulatorException(String.format("Unknown port state %s", state.toString()));
        }
        number = portNumber;
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
