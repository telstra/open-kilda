package org.openkilda.messaging.command.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.command.CommandData;

import java.util.Objects;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "requester"})

/*
 *  Command to request a list of all ports including port state
 */
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

    @Override
    public String toString() {
        return "PortsCommandData{" +
                "requester='" + requester + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortsCommandData)) return false;
        PortsCommandData that = (PortsCommandData) o;
        return Objects.equals(getRequester(), that.getRequester());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getRequester());
    }
}
