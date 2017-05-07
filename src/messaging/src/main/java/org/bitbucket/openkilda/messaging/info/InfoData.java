package org.bitbucket.openkilda.messaging.info;

import org.bitbucket.openkilda.messaging.DestinationType;
import org.bitbucket.openkilda.messaging.MessageData;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsStatusResponse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the payload of a Message representing an info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "info",
        "destination"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "info",
        visible = true)
@JsonSubTypes({
        @Type(value = FlowResponse.class, name = "flow"),
        @Type(value = FlowsResponse.class, name = "flows"),
        @Type(value = FlowStatusResponse.class, name = "flow_status"),
        @Type(value = FlowsStatusResponse.class, name = "flows_status"),
        @Type(value = FlowPathResponse.class, name = "flow_path"),
        @Type(value = PathInfoData.class, name = "path"),
        @Type(value = IslInfoData.class, name = "isl"),
        @Type(value = SwitchInfoData.class, name = "switch"),
        @Type(value = PortInfoData.class, name = "port")})
public abstract class InfoData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message info.
     */
    @JsonProperty("info")
    private String info;

    /**
     * Message destination.
     */
    @JsonProperty("destination")
    private DestinationType destination;

    /**
     * Default constructor.
     */
    public InfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param   info         message info
     * @param   destination  message destination
     */
    @JsonCreator
    public InfoData(@JsonProperty("info") final String info,
                    @JsonProperty("destination") final DestinationType destination) {
        setInfo(info);
        setDestination(destination);
    }

    /**
     * Returns message info.
     *
     * @return  message info
     */
    @JsonProperty("info")
    public String getInfo() {
        return info;
    }

    /**
     * Sets message info.
     *
     * @param   info  message info
     */
    @JsonProperty("info")
    public void setInfo(final String info) {
        this.info = info;
    }

    /**
     * Returns message destination.
     *
     * @return  message destination
     */
    @JsonProperty("destination")
    public DestinationType getDestination() {
        return destination;
    }

    /**
     * Sets message destination.
     *
     * @param   destination  message destination
     */
    @JsonProperty("destination")
    public void setDestination(final DestinationType destination) {
        this.destination = destination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s -> %s", info, destination);
    }
}
