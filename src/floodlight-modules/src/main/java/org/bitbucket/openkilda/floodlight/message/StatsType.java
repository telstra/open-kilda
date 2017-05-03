package org.bitbucket.openkilda.floodlight.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum StatsType {
    @JsonProperty("ports")PORTS,
    @JsonProperty("flows")FLOWS,
    @JsonProperty("meters")METERS
}
