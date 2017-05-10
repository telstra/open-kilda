package org.bitbucket.openkilda.messaging.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the data payload of a Message representing an command for ISL discovery.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "switch_id",
        "port_no"})
public class DiscoverIslCommandData extends CommandData {
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
    private int portNo;

    /**
     * Default constructor.
     */
    public DiscoverIslCommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param switchId switch id
     * @param portNo   port number
     */
    @JsonCreator
    public DiscoverIslCommandData(@JsonProperty("switch_id") final String switchId,
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
    public void setPortNo(int portNo) {
        this.portNo = portNo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s:%s", switchId, portNo);
    }
}
