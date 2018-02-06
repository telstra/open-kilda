package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.ctrl.AbstractDumpState;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CacheBoltState extends AbstractDumpState {
    @JsonProperty("network")
    private NetworkDump network;

    @JsonProperty("flow")
    private FlowDump flow;

    @JsonCreator
    public CacheBoltState(
            @JsonProperty("network") NetworkDump network,
            @JsonProperty("flow") FlowDump flow) {
        this.network = network;
        this.flow = flow;
    }

    public NetworkDump getNetwork() {
        return network;
    }

    public FlowDump getFlow() {
        return flow;
    }
}
