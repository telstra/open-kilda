package org.openkilda.messaging.info.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.PortInfoData;

import java.util.Objects;
import java.util.Set;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "switch_id",
        "ports"})

/*
 * All ports for a switch
 */

public class SwitchPortsData extends InfoData {
    private static final long serialVersionUID = 1L;

    @JsonProperty("requester")
    private String requester;

    @JsonProperty("ports")
    private Set<PortInfoData> ports;

    public SwitchPortsData() {

    }

    @JsonCreator
    public SwitchPortsData(Set<PortInfoData> ports, String requester) {
        this.ports = ports;
        this.requester = requester;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public Set<PortInfoData> getPorts() {
        return ports;
    }

    public void setPorts(Set<PortInfoData> ports) {
        this.ports = ports;
    }

    @Override
    public String toString() {
        return "SwitchPortsData{" +
                "requester='" + requester + '\'' +
                ", ports=" + ports +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SwitchPortsData)) return false;
        SwitchPortsData that = (SwitchPortsData) o;
        return Objects.equals(getRequester(), that.getRequester()) &&
                Objects.equals(getPorts(), that.getPorts());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getRequester(), getPorts());
    }
}
