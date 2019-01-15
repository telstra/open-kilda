package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.istack.Nullable;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;


@Value
@Builder
public class FlowCreate implements Serializable {

    @JsonProperty("flowid")
    private String flowid;

    @JsonProperty("length")
    private int length;

    @JsonProperty("error")
    private FlowCreateError error;

    @JsonCreator
    public FlowCreate(@JsonProperty("flowid") String flowid,
                      @JsonProperty("length") int length,
                      @JsonProperty("error") @Nullable FlowCreateError error) {
        this.flowid = flowid;
        this.length = length;
        this.error = error;
    }

}
