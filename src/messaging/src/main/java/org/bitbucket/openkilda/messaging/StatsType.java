package org.bitbucket.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum StatsType {
    @JsonProperty("ports")PORTS,
    @JsonProperty("flows")FLOWS,
    @JsonProperty("meters")METERS
}
