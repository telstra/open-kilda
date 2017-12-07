package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;

import java.io.Serializable;
import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowDump implements Serializable {
    @JsonProperty("flows")
    private Set<ImmutablePair<Flow, Flow>> flows;

    @JsonCreator
    public FlowDump(
            @JsonProperty("flows") Set<ImmutablePair<Flow, Flow>> flows) {
        this.flows = flows;
    }
}
