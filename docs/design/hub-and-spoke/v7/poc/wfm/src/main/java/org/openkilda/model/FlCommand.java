package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.io.Serializable;

@Getter
public class FlCommand implements Serializable {
    @JsonProperty("flowid")
    private String flowid;

    @JsonProperty("ruleid")
    private int ruleId;

    @JsonProperty("error")
    private FlowCreateError error;

    @JsonCreator
    public FlCommand(@JsonProperty("flowid") String flowid,
                     @JsonProperty("ruleid") int ruleId,
                     @JsonProperty("error") FlowCreateError error) {
        this.flowid = flowid;
        this.ruleId = ruleId;
        this.error = error;
    }
}
