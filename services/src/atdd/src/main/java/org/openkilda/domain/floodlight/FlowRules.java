package org.openkilda.domain.floodlight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowRules {

    private List<FlowItem> flows;

    public FlowRules() {
    }

    public List<FlowItem> getFlows() {
        return flows;
    }

    public void setFlows(List<FlowItem> flows) {
        this.flows = flows;
    }
}
