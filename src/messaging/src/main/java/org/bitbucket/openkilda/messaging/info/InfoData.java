package org.bitbucket.openkilda.messaging.info;

import org.bitbucket.openkilda.messaging.MessageData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents high level view of data for info messages.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "message_type")
@JsonSubTypes({
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
     * Info message type.
     */
    @JsonProperty("message_type")
    private InfoMessageType messageType;

    /**
     * Default constructor.
     */
    public InfoData() {
    }

    /**
     * Returns info message type.
     *
     * @return info message type
     */
    @JsonProperty("message_type")
    public InfoMessageType getType() {
        return messageType;
    }

    /**
     * Sets info message type.
     *
     * @param messageType info message type
     */
    @JsonProperty("message_type")
    public void setType(final InfoMessageType messageType) {
        this.messageType = messageType;
    }
}
