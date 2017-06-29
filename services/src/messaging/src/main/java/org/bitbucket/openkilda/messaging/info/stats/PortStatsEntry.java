package org.bitbucket.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TODO: add javadoc.
 */
public class PortStatsEntry {

    @JsonProperty
    private int portNo;

    @JsonProperty
    private long rxPackets;

    @JsonProperty
    private long txPackets;

    @JsonProperty
    private long rxBytes;

    @JsonProperty
    private long txBytes;

    @JsonProperty
    private long rxDropped;

    @JsonProperty
    private long txDropped;

    @JsonProperty
    private long rxErrors;

    @JsonProperty
    private long txErrors;

    @JsonProperty
    private long rxFrameErr;

    @JsonProperty
    private long rxOverErr;

    @JsonProperty
    private long rxCrcErr;

    @JsonProperty
    private long collisions;

    @JsonCreator
    public PortStatsEntry(@JsonProperty("portNo") int portNo,
                          @JsonProperty("rxPackets") long rxPackets,
                          @JsonProperty("txPackets") long txPackets,
                          @JsonProperty("rxBytes") long rxBytes,
                          @JsonProperty("txBytes") long txBytes,
                          @JsonProperty("rxDropped") long rxDropped,
                          @JsonProperty("txDropped") long txDropped,
                          @JsonProperty("rxErrors") long rxErrors,
                          @JsonProperty("txErrors") long txErrors,
                          @JsonProperty("rxFrameErr") long rxFrameErr,
                          @JsonProperty("rxOverErr") long rxOverErr,
                          @JsonProperty("rxCrcErr") long rxCrcErr,
                          @JsonProperty("collisions") long collisions) {
        this.portNo = portNo;
        this.rxPackets = rxPackets;
        this.txPackets = txPackets;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
        this.rxDropped = rxDropped;
        this.txDropped = txDropped;
        this.rxErrors = rxErrors;
        this.txErrors = txErrors;
        this.rxFrameErr = rxFrameErr;
        this.rxOverErr = rxOverErr;
        this.rxCrcErr = rxCrcErr;
        this.collisions = collisions;
    }

    public int getPortNo() {
        return portNo;
    }

    public long getRxPackets() {
        return rxPackets;
    }

    public long getTxPackets() {
        return txPackets;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public long getRxDropped() {
        return rxDropped;
    }

    public long getTxDropped() {
        return txDropped;
    }

    public long getRxErrors() {
        return rxErrors;
    }

    public long getTxErrors() {
        return txErrors;
    }

    public long getRxFrameErr() {
        return rxFrameErr;
    }

    public long getRxOverErr() {
        return rxOverErr;
    }

    public long getRxCrcErr() {
        return rxCrcErr;
    }

    public long getCollisions() {
        return collisions;
    }
}
