package org.bitbucket.openkilda.northbound.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Flow endpoint representation class.
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowEndpoint implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The switch id.
     */
    @JsonProperty("switch-id")
    private String switchId;

    /**
     * The port id.
     */
    @JsonProperty("port-id")
    private String portId;

    /**
     * The port id.
     */
    @JsonProperty("vlan-id")
    private Number vlanId;

    /**
     * Default constructor.
     */
    public FlowEndpoint() {
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId - switch id
     */
    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    /**
     * Returns port id.
     *
     * @return port id
     */
    public String getPortId() {
        return portId;
    }

    /**
     * Sets port id.
     *
     * @param portId - port id
     */
    public void setPortId(final String portId) {
        this.portId = portId;
    }

    /**
     * Returns vlan id.
     *
     * @return vlan id
     */
    public Number getVlanId() {
        return vlanId;
    }

    /**
     * Sets vlan id.
     *
     * @param vlanId - vlan id
     */
    public void setVlanId(final Number vlanId) {
        this.vlanId = vlanId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || !(obj instanceof FlowEndpoint)) {
            return false;
        }

        FlowEndpoint that = (FlowEndpoint) obj;
        return Objects.equals(this.getSwitchId(), that.getSwitchId())
                && Objects.equals(this.getPortId(), that.getPortId())
                && Objects.equals(this.getVlanId(), that.getVlanId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, portId, vlanId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switch-id", switchId)
                .add("port-id", portId)
                .add("vlan-id", vlanId)
                .toString();
    }
}
