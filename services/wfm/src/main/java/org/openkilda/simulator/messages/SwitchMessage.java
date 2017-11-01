package org.openkilda.simulator.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid",
        "num_of_ports",
        "links"})

public class SwitchMessage implements Serializable {
    @JsonProperty("dpid")
    private String dpid;

    @JsonProperty("num_of_ports")
    private int numOfPorts;

    @JsonProperty("links")
    private List<LinkMessage> links;

    public SwitchMessage(@JsonProperty("dpid") String dpid,
                         @JsonProperty("num_of_ports") int numOfPorts,
                         @JsonProperty("links") List<LinkMessage> links) {
        this.dpid = dpid;
        this.numOfPorts = numOfPorts;
        this.links = links;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("dpid", dpid)
                .add("num_of_ports", numOfPorts)
                .toString();
    }

    public String getDpid() {
        return dpid;
    }

    public int getNumOfPorts() {
        return numOfPorts;
    }

    public List<LinkMessage> getLinks() { return links; }
}
