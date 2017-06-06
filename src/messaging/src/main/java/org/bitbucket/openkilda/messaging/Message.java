package org.bitbucket.openkilda.messaging;

import static com.google.common.base.Objects.toStringHelper;
import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.TIMESTAMP;

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
        TIMESTAMP,
        CORRELATION_ID,
        "payload"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = CommandMessage.class, name = "COMMAND"),
        @JsonSubTypes.Type(value = InfoMessage.class, name = "INFO"),
        @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")})
public class Message implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message timestamp.
     */
    @JsonProperty(TIMESTAMP)
    protected long timestamp;

    /**
     * Message correlation id.
     * Correlation ID request value for Northbound messages or generated value without REST API calls (re-flow, etc.).
     */
    @JsonProperty(CORRELATION_ID)
    protected String correlationId;

    /**
     * Instance constructor.
     *
     * @param timestamp     message timestamp
     * @param correlationId message correlation id
     */
    @JsonCreator
    public Message(@JsonProperty(TIMESTAMP) final long timestamp,
                   @JsonProperty(CORRELATION_ID) final String correlationId) {
        this.timestamp = timestamp;
        this.correlationId = correlationId;
    }

    /**
     * Returns message timestamp.
     *
     * @return message timestamp
     */
    @JsonProperty(TIMESTAMP)
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns message correlation id.
     *
     * @return message correlation id
     */
    @JsonProperty(CORRELATION_ID)
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add(CORRELATION_ID, correlationId)
                .toString();
    }
}

