package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowDump implements Serializable {
    @JsonProperty("flows")
    private Set<FlowPair<Flow, Flow>> flows;

    @JsonCreator
    public FlowDump(
            @JsonProperty("flows") Set<FlowPair<Flow, Flow>> flows) {
        this.flows = flows;
    }

    public Set<FlowPair<Flow, Flow>> getFlows() {
        return flows;
    }
}
