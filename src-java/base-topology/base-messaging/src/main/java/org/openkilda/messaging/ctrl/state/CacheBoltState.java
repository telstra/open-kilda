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

    @JsonCreator
    public CacheBoltState(
            @JsonProperty("network") NetworkDump network) {
        this.network = network;
    }

    public NetworkDump getNetwork() {
        return network;
    }

    public void accept(DumpStateVisitor visitor) {
        visitor.visit(this);
    }
}
