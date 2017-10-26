package org.openkilda.simulator.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

import static com.google.common.base.MoreObjects.toStringHelper;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "latency",
        "local_port",
        "peer_switch",
        "peer_port"})

public class LinkMessage implements Serializable {
    @JsonProperty("latency")
    private int latency;

    @JsonProperty("local_port")
    private int localPort;

    @JsonProperty("peer_switch")
    private String peerSwitch;

    @JsonProperty("peer_port")
    private int peerPort;

    public LinkMessage(@JsonProperty("latency") int latency,
                       @JsonProperty("peer_switch") String peerSwitch,
                       @JsonProperty("peer_port") int peerPort) {
        this.latency = latency;
        this.peerSwitch = peerSwitch;
        this.peerPort = peerPort;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("latency", latency)
                .toString();
    }

    public int getLatency() {
        return latency;
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getPeerSwitch() {
        return peerSwitch;
    }

    public int getPeerPort() {
        return peerPort;
    }
}
