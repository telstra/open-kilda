package org.openkilda.simulator.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "type",
        "switches",
        "links"})

public class TopologyMessage extends SimulatorMessage {
    @JsonProperty("switches")
    private List<SwitchMessage> switches;

    public TopologyMessage(@JsonProperty("switches") final List<SwitchMessage> switches,
                           @JsonProperty("links") final List<LinkMessage> links) {
        this.switches = switches;
    }

    public List<SwitchMessage> getSwitches() {
        return switches;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switches", switches.toString())
                .toString();
    }
}
