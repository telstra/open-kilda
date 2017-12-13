package org.openkilda.messaging.ctrl.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.ctrl.AbstractDumpState;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OFELinkBoltState extends AbstractDumpState {
    @JsonProperty("discovery")
    private final List<?> discovery;

    @JsonProperty("filtered")
    private final Set<?> filtered;

    @JsonCreator
    public OFELinkBoltState(
            @JsonProperty("state") List<?> discovery,
            @JsonProperty("filtered") Set<?> filtered) {
        this.discovery = discovery;
        this.filtered = filtered;
    }
}
