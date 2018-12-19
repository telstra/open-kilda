package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.istack.Nullable;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
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
                     @JsonProperty("error") @Nullable FlowCreateError error) {
        this.flowid = flowid;
        this.ruleId = ruleId;
        this.error = error;
    }
}
