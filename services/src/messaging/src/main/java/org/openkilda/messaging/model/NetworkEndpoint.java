package org.openkilda.messaging.model;

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;

import java.io.Serializable;
import java.util.Objects;

public class NetworkEndpoint implements Serializable {
    @JsonProperty("switch-id")
    private final String switchDpId;

    @JsonProperty("port-id")
    private final Integer portId;

    @JsonCreator
    public NetworkEndpoint(
            @JsonProperty("switch-id") String switchDpId,
            @JsonProperty("port-id") Integer portId) {
        if (!Utils.validateSwitchId(switchDpId)) {
            throw new IllegalArgumentException(String.format("Invalid switch DPID: %s", switchDpId));
        }
        if (portId == null || portId < 0) {
            throw new IllegalArgumentException(String.format("Invalid portId: %s", portId));
        }

        this.switchDpId = switchDpId;
        this.portId = portId;
    }

    public NetworkEndpoint(NetworkEndpoint that) {
        this(that.switchDpId, that.portId);
    }

    public String getSwitchDpId() {
        return switchDpId;
    }

    public Integer getPortId() {
        return portId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NetworkEndpoint that = (NetworkEndpoint) obj;
        return new EqualsBuilder()
                .append(switchDpId, that.switchDpId)
                .append(portId, that.portId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(switchDpId, portId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("switchDpId", switchDpId)
                .add("portId", portId)
                .toString();
    }
}
