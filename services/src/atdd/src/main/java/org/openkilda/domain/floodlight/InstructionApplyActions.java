package org.openkilda.domain.floodlight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstructionApplyActions {
    private FlowActions flowActions;

    public InstructionApplyActions() {
    }

    public FlowActions getFlowActions() {
        return flowActions;
    }

    public void setFlowActions(FlowActions flowActions) {
        this.flowActions = flowActions;
    }
}
