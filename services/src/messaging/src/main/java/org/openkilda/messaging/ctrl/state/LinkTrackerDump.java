package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkTrackerDump implements Serializable {
    @JsonProperty("state")
    Map<String, Map<String, AtomicInteger>> state;

    @JsonCreator
    public LinkTrackerDump(
            @JsonProperty("state") Map<String, Map<String, AtomicInteger>> state) {
        this.state = state;
    }
}
