package org.bitbucket.openkilda.messaging.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    public PortStatsEntry(@JsonProperty int portNo, @JsonProperty long rxPackets, @JsonProperty long txPackets,
                          @JsonProperty long rxBytes, @JsonProperty long txBytes, @JsonProperty long rxDropped,
                          @JsonProperty long txDropped, @JsonProperty long rxErrors, @JsonProperty long txErrors,
                          @JsonProperty long rxFrameErr, @JsonProperty long rxOverErr, @JsonProperty long rxCrcErr,
                          @JsonProperty long collisions) {
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
}
