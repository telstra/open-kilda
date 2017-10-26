package org.openkilda.simulator.classes;

import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Random;

public class Port {
    private final int MAX_SMALL = 50;
    private final int MAX_LARGE = 10000;

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
    protected boolean isActive = true;
    protected boolean isForwarding = true;
    protected int latency = 0;
    protected DatapathId peerSwitch;
    protected int peerPortNum = -1;

    public Port(int number) {
        this(number, true, true);
    }

    public Port(int number, boolean isActive, boolean isForwardig) {
        this.number = number;
        this.isActive = isActive;
        this.isForwarding = isForwardig;
    }

    public void disable(boolean isActive) {
        this.isActive = !isActive;
        if (!this.isActive) {
            block();
        }
    }

    public void block() {
        this.isForwarding = false;
    }

    public void unblock() {
        this.isForwarding = true;
        this.isActive = true;
    }

    public int getNumber() {
        return number;
    }

    public void isl(DatapathId peerSwitch, int perPortNum) {
        this.peerSwitch = peerSwitch;
        this.peerPortNum = perPortNum;
    }

    public boolean isActiveIsl() {
        return (peerSwitch != null && peerPortNum >= 0 && isActive && isForwarding);
    }

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

    public void setLatency(int latency) {
        this.latency = latency;
    }

    public int getLatency() {
        return latency;
    }

    public String getPeerSwitch() {
        return peerSwitch.toString();
    }

    public void setPeerSwitch(String peerSwitch) {
        this.peerSwitch = DatapathId.of(peerSwitch);
    }

    public void setPeerPortNum(int peerPortNum) {
        this.peerPortNum = peerPortNum;
    }

    public int getPeerPortNum() { return peerPortNum; }


}
