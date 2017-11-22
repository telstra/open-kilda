package org.openkilda.simulator.messages.simulator.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.simulator.messages.LinkMessage;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "dpid",
        "link"})

public class AddLinkCommandMessage extends SimulatorCommandMessage {
    @JsonProperty("dpid")
    private String dpid;

    @JsonProperty("link")
    private LinkMessage link;

    public AddLinkCommandMessage() {

    }

    public AddLinkCommandMessage(@JsonProperty("dpid") String dpid,
                                 @JsonProperty("link") LinkMessage link) {
        this.dpid = dpid;
        this.link = link;
    }

    @Override
    public String getDpid() {
        return dpid;
    }

    @Override
    public void setDpid(String dpid) {
        this.dpid = dpid;
    }

    public LinkMessage getLink() {
        return link;
    }

    public void setLink(LinkMessage link) {
        this.link = link;
    }
}
