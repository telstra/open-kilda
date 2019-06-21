package org.openkilda.messaging.ctrl.state;

import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateVisitor;
import org.openkilda.messaging.model.DiscoveryLink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OFELinkBoltState extends AbstractDumpState {

    @JsonProperty("discovery")
    private final Set<DiscoveryLink> discovery;

    @JsonProperty("filtered")
    private final Set<DiscoveryLink> filtered;

    @JsonCreator
    public OFELinkBoltState(
            @JsonProperty("state") Set<DiscoveryLink> discovery,
            @JsonProperty("filtered") Set<DiscoveryLink> filtered) {
        this.discovery = discovery;
        this.filtered = filtered;
    }

    public void accept(DumpStateVisitor visitor) {
        visitor.visit(this);
    }

    public Set<DiscoveryLink> getDiscovery() {
        return discovery;
    }

    public Set<DiscoveryLink> getFiltered() {
        return filtered;
    }
}
