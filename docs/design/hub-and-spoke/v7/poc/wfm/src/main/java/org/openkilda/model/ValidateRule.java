package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;


public class ValidateRule extends FlCommand {
    @JsonProperty("command")
    private final String command = "validate";

    @Builder
    @JsonCreator
    public ValidateRule(@JsonProperty("flowid") String flowid,
                        @JsonProperty("ruleid") int ruleid,
                        @JsonProperty("error") FlowCreateError error) {
        super(flowid, ruleid, error);
    }
}
