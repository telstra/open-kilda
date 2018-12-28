package org.openkilda.messaging.command.discovery;

import org.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "requester"})

/*
 *  Command to request a list of all ports including port state
 */
@EqualsAndHashCode
@ToString(callSuper = true)
public class PortsCommandData extends CommandData {
    private static final long serialVersionUID = 1L;

    @JsonProperty("requester")
    private String requester;

    public PortsCommandData() {
    }

    @JsonCreator
    public PortsCommandData(String requester) {
        this.requester = requester;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }
}
