package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateVisitor;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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

    // TODO: During merge, found duplicate methods .. refactor into one set .. getNetwork / getNetworkDump .. getFlow / getFlowDump
    public NetworkDump getNetwork() {
        return network;
    }

    public FlowDump getFlow() {
        return flow;
    }

    public void accept(DumpStateVisitor visitor) {
        visitor.visit(this);
    }

    public NetworkDump getNetworkDump() {
        return network;
    }

    public FlowDump getFlowDump() {
        return flow;
    }
}
