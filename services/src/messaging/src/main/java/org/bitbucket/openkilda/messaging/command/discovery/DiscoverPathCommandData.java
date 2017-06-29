package org.bitbucket.openkilda.messaging.command.discovery;

import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the payload payload of a Message representing an command for path discovery.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "source_switch_id",
        "source_port_no",
        "destination_switch_id"})
public class DiscoverPathCommandData extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Source switch id.
     */
    @JsonProperty("source_switch_id")
    private String srcSwitchId;

    /**
     * Source port number.
     */
    @JsonProperty("source_port_no")
    private int srcPortNo;

    /**
     * Destination switch id.
     */
    @JsonProperty("destination_switch_id")
    private String dstSwitchId;

    /**
     * Default constructor.
     */
    public DiscoverPathCommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param srcSwitchId source switch id
     * @param srcPortNo   source ort number
     * @param dstSwitchId destination switch id
     */
    @JsonCreator
    public DiscoverPathCommandData(@JsonProperty("source_switch_id") final String srcSwitchId,
                                   @JsonProperty("source_port_no") final int srcPortNo,
                                   @JsonProperty("destination_switch_id") final String dstSwitchId) {
        this.srcSwitchId = srcSwitchId;
        this.srcPortNo = srcPortNo;
        this.dstSwitchId = dstSwitchId;
    }

    /**
     * Returns source switch id.
     *
     * @return source switch id
     */
    public String getSrcSwitchId() {
        return srcSwitchId;
    }

    /**
     * Sets source switch id.
     *
     * @param switchId source switch id to set
     */
    public void setSrcSwitchId(String switchId) {
        this.srcSwitchId = switchId;
    }

    /**
     * Returns source port number.
     *
     * @return source port number
     */
    public int getSrcPortNo() {
        return srcPortNo;
    }

    /**
     * Sets source port number.
     *
     * @param portNo source port number to set
     */
    public void setSrcPortNo(int portNo) {
        this.srcPortNo = portNo;
    }

    /**
     * Returns gets destination switch id.
     *
     * @return switch id
     */
    public String getDstSwitchId() {
        return dstSwitchId;
    }

    /**
     * Sets destination switch id.
     *
     * @param switchId destination switch id to set
     */
    public void setDstSwitchId(String switchId) {
        this.dstSwitchId = switchId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s-%s -> %s", srcSwitchId, srcPortNo, dstSwitchId);
    }
}
