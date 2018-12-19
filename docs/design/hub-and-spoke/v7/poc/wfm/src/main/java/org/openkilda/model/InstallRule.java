package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.istack.Nullable;
import lombok.Value;

@Value
public class InstallRule extends FlCommand {
    @JsonCreator
    public InstallRule(@JsonProperty("flowid") String flowid,
                     @JsonProperty("ruleid") int ruleid,
                     @JsonProperty("error") @Nullable FlowCreateError error) {
        super(flowid, ruleid, error);
    }
}
