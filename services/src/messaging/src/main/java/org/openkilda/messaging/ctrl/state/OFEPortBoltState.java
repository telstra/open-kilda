package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.ctrl.AbstractDumpState;

import java.util.Map;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OFEPortBoltState extends AbstractDumpState {
    @JsonProperty("state")
    Map<String, Map<String, String>> state;

    @JsonCreator
    public OFEPortBoltState(
            @JsonProperty("state") Map<String, Map<String, String>> state) {
        this.state = state;
    }
}
