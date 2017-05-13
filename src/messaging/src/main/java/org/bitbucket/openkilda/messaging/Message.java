package org.bitbucket.openkilda.messaging;

import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.info.InfoMessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * Class represents high level view of every message used by any service.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "timestamp",
        "correlation-id",
        "payload"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = CommandMessage.class, name = "COMMAND"),
        @JsonSubTypes.Type(value = InfoMessage.class, name = "INFO"),
        @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")})
public abstract class Message implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message type.
     */
    @JsonProperty("type")
    private MessageType type;

    /**
     * Message timestamp.
     */
    @JsonProperty("timestamp")
    private long timestamp;

    /**
     * Message correlation id.
     * Correlation ID request value for Northbound messages or generated value without REST API calls (re-flow, etc.).
     */
    @JsonProperty("correlation_id")
    private String correlationId;

    /**
     * Default constructor.
     */
    public Message() {
    }

    /**
     * Instance constructor.
     *
     * @param   type           message type
     * @param   timestamp      message timestamp
     * @param   correlationId  message correlation id
     */
    @JsonCreator
    public Message(@JsonProperty("type") final MessageType type,
                   @JsonProperty("timestamp") final long timestamp,
                   @JsonProperty("correlation_id") final String correlationId) {
        this.type = type;
        this.timestamp = timestamp;
        this.correlationId = correlationId;
    }

    /**
     * Returns message type.
     *
     * @return  message type
     */
    @JsonProperty("type")
    public MessageType getType() {
        return type;
    }

    /**
     * Sets message type.
     *
     * @param   type  message type
     */
    @JsonProperty("type")
    public void setType(final MessageType type) {
        this.type = type;
    }

    /**
     * Returns message timestamp.
     *
     * @return  message timestamp
     */
    @JsonProperty("timestamp")
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets message timestamp.
     *
     * @param   timestamp  message timestamp
     */
    @JsonProperty("timestamp")
    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns message correlation id.
     *
     * @return  message correlation id
     */
    @JsonProperty("correlation_id")
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets message correlation id.
     *
     * @param   correlationId  message correlation id
     */
    @JsonProperty("correlation_id")
    public void setCorrelationId(final String correlationId) {
        this.correlationId = correlationId;
    }
}
