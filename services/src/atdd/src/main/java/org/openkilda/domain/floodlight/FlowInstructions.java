package org.openkilda.domain.floodlight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowInstructions {

    @JsonProperty(value = "instruction_apply_actions")
    private InstructionApplyActions instructionApplyActions;
    private String none;

    public FlowInstructions() {
    }

    public InstructionApplyActions getInstructionApplyActions() {
        return instructionApplyActions;
    }

    public void setInstructionApplyActions(InstructionApplyActions instructionApplyActions) {
        this.instructionApplyActions = instructionApplyActions;
    }

    public String getNone() {
        return none;
    }

    public void setNone(String none) {
        this.none = none;
    }
}
