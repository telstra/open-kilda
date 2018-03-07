package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class IPerfReportSumBranch implements Serializable {
    public Long packets;
    public Long bytes;

    @JsonProperty("lost_packets")
    public Long lostPackets;
    @JsonProperty("lost_percent")
    public Float lostPercent;

    public Double seconds;
    @JsonProperty("bits_per_second")
    public Double bitsPerSecond;
}
