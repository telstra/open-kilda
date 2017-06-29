package org.bitbucket.openkilda.messaging.payload.flow;

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
        "vlan-id",
        "meter-id"})
public class FlowEndpointPayload implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Allocated meter id.
     */
    @JsonProperty("meter_id")
    protected Long meterId;

    /**
     * The switch id.
     */
    @JsonProperty("switch-id")
    private String switchId;

    /**
     * The port id.
     */
    @JsonProperty("port-id")
    private Integer portId;

    /**
     * The port id.
     */
    @JsonProperty("vlan-id")
    private Integer vlanId;

    /**
     * Instance constructor.
     *
     * @param switchId switch id
     * @param portId   port id
     * @param vlanId   vlan id
     * @param meterId  meter id
     */
    @JsonCreator
    public FlowEndpointPayload(@JsonProperty("switch-id") final String switchId,
                               @JsonProperty("port-id") final Integer portId,
                               @JsonProperty("vlan-id") final Integer vlanId,
                               @JsonProperty("meter-id") final Long meterId) {
        setSwitchId(switchId);
        setPortId(portId);
        setVlanId(vlanId);
        setMeterId(meterId);
    }

    /**
     * Instance constructor.
     *
     * @param switchId switch id
     * @param portId   port id
     * @param vlanId   vlan id
     */
    public FlowEndpointPayload(final String switchId, final Integer portId, final Integer vlanId) {
        setSwitchId(switchId);
        setPortId(portId);
        setVlanId(vlanId);
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
     * @param switchId switch id
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
     * @return port id
     */
    public Integer getPortId() {
        return portId;
    }

    /**
     * Sets port id.
     *
     * @param portId port id
     */
    public void setPortId(final Integer portId) {
        if (portId == null) {
            throw new IllegalArgumentException("need to set port id");
        } else if (portId < 0L) {
            throw new IllegalArgumentException("need to set positive value for port id");
        }
        this.portId = portId;
    }

    /**
     * Returns vlan id.
     *
     * @return vlan id
     */
    public Integer getVlanId() {
        return vlanId;
    }

    /**
     * Sets vlan id.
     *
     * @param vlanId vlan id
     */
    public void setVlanId(final Integer vlanId) {
        if (vlanId == null) {
            this.vlanId = 0;
        } else if (Utils.validateVlanRange(vlanId)) {
            this.vlanId = vlanId;
        } else {
            throw new IllegalArgumentException("need to set valid value for vlan id");
        }
    }

    /**
     * Returns meter id for the flow.
     *
     * @return meter id for the flow
     */
    public Long getMeterId() {
        return meterId;
    }

    /**
     * Sets meter id for the flow.
     *
     * @param meterId meter id for the flow
     */
    public void setMeterId(final Long meterId) {
        this.meterId = meterId;
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
                && Objects.equals(getVlanId(), that.getVlanId())
                && Objects.equals(getMeterId(), that.getMeterId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, portId, vlanId, meterId);
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
                .add("meter-id", meterId)
                .toString();
    }
}
