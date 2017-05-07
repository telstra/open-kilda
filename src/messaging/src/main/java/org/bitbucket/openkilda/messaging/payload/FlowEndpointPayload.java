package org.bitbucket.openkilda.messaging.payload;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Flow endpoint representation class.
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "switch-id",
        "port-id",
        "vlan-id"})
public class FlowEndpointPayload implements Serializable {
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
    private Number portId;

    /**
     * The port id.
     */
    @JsonProperty("vlan-id")
    private Number vlanId;

    /**
     * Default constructor.
     */
    public FlowEndpointPayload() {
    }

    /**
     * Instance constructor.
     *
     * @param switchId  switch id
     * @param portId    port id
     * @param vlanId    vlan id
     */
    @JsonCreator
    public FlowEndpointPayload(@JsonProperty("switch-id") final String switchId,
                               @JsonProperty("port-id") final Number portId,
                               @JsonProperty("vlan-id") final Number vlanId) {
        setSwitchId(switchId);
        setPortId(portId);
        setVlanId(vlanId);
    }

    /**
     * Returns switch id.
     *
     * @return  switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param   switchId  switch id
     */
    public void setSwitchId(final String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set switch id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch id");
        }

        this.switchId = switchId;
    }

    /**
     * Returns port id.
     *
     * @return  port id
     */
    public Number getPortId() {
        return portId;
    }

    /**
     * Sets port id.
     *
     * @param   portId  port id
     */
    public void setPortId(final Number portId) {
        if (portId == null) {
            throw new IllegalArgumentException("need to set port id");
        } else if (portId.intValue() < 0) {
            throw new IllegalArgumentException("need to set positive value for port id");
        }
        this.portId = portId;
    }

    /**
     * Returns vlan id.
     *
     * @return  vlan id
     */
    public Number getVlanId() {
        return vlanId;
    }

    /**
     * Sets vlan id.
     *
     * @param   vlanId  vlan id
     */
    public void setVlanId(final Number vlanId) {
        if (vlanId == null) {
            this.vlanId = 0;
        } else if (Utils.validateVlanRange(vlanId.intValue())) {
            this.vlanId = vlanId;
        } else {
            throw new IllegalArgumentException("need to set valid value for vlan id");
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || !(obj instanceof FlowEndpointPayload)) {
            return false;
        }

        FlowEndpointPayload that = (FlowEndpointPayload) obj;
        return Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getPortId(), that.getPortId())
                && Objects.equals(getVlanId(), that.getVlanId());
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
