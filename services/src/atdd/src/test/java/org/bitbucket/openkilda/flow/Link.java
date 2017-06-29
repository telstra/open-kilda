package org.bitbucket.openkilda.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Link {
    @JsonProperty("src_switch")
    private String sourceSwitch;
    @JsonProperty("dst_switch")
    private String destinationSwitch;
    @JsonProperty("src_port")
    private String sourcePort;
    @JsonProperty("dst_port")
    private String destinationPort;
    @JsonProperty("latency")
    private String latency;
    @JsonProperty("speed")
    private String speed;
    @JsonProperty("available_bandwidth")
    private int availableBandwidth;

    public Link(@JsonProperty("src_switch") String sourceSwitch,
                @JsonProperty("dst_switch") String destinationSwitch,
                @JsonProperty("src_port") String sourcePort,
                @JsonProperty("dst_port") String destinationPort,
                @JsonProperty("latency") String latency,
                @JsonProperty("speed") String speed,
                @JsonProperty("available_bandwidth") int availableBandwidth) {
        this.sourceSwitch = sourceSwitch;
        this.destinationSwitch = destinationSwitch;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.latency = latency;
        this.speed = speed;
        this.availableBandwidth = availableBandwidth;
    }

    public String getSourceSwitch() {
        return sourceSwitch;
    }

    public String getSourcePort() {
        return sourcePort;
    }

    public int getAvailableBandwidth() {
        return availableBandwidth;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("src_switch", sourceSwitch)
                .add("src_port", sourcePort)
                .add("dst_switch", destinationSwitch)
                .add("dst_port", destinationPort)
                .add("latency", latency)
                .add("speed", speed)
                .add("available_bandwidth", availableBandwidth)
                .toString();
    }
}
