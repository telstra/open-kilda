package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.ctrl.AbstractDumpState;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrudBoltState extends AbstractDumpState {
    @JsonProperty("flow")
    private FlowDump flow;

    @JsonCreator
    public CrudBoltState(
            @JsonProperty("flow") FlowDump flow) {
        this.flow = flow;
    }
}
