package org.openkilda.messaging.info.event;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NetworkTopologyChange extends InfoData {
    @JsonProperty("type")
    private final NetworkTopologyChangeType type;

    @JsonProperty("switch_id")
    private final String switchId;

    @JsonProperty("port_number")
    private final int portNumber;

    @JsonCreator
    public NetworkTopologyChange(
            @JsonProperty("type") NetworkTopologyChangeType type,
            @JsonProperty("switch_id") String switchId,
            @JsonProperty("port_number") int portNumber) {
        this.type = type;
        this.switchId = switchId;
        this.portNumber = portNumber;
    }

    public NetworkTopologyChangeType getType() {
        return type;
    }

    public String getSwitchId() {
        return switchId;
    }

    public int getPortNumber() {
        return portNumber;
    }
}
