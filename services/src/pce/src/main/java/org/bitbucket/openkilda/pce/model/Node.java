package org.bitbucket.openkilda.pce.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents node entity.
 */
@JsonSerialize
public class Node implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Switch id.
     */
    @JsonProperty("switch_id")
    protected String switchId;

    /**
     * Port number.
     */
    @JsonProperty("port_no")
    protected int portNo;

    /**
     * Default constructor.
     */
    public Node() {
    }

    /**
     * Instance creator.
     *
     * @param switchId switch id
     * @param portNo   port number
     */
    @JsonCreator
    public Node(@JsonProperty("switch_id") final String switchId,
                @JsonProperty("port_no") final int portNo) {
        this.switchId = switchId;
        this.portNo = portNo;
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id to set
     */
    @JsonProperty("switch_id")
    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    /**
     * Returns port number.
     *
     * @return port number
     */
    @JsonProperty("port_no")
    public int getPortNo() {
        return portNo;
    }

    /**
     * Sets port number.
     *
     * @param portNo port number to set
     */
    @JsonProperty("port_no")
    public void setPortNo(final int portNo) {
        this.portNo = portNo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switch_id", switchId)
                .add("port_no", portNo)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, portNo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        Node that = (Node) object;
        return Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getPortNo(), that.getPortNo());
    }
}
