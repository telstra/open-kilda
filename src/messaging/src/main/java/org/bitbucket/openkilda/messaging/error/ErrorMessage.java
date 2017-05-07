package org.bitbucket.openkilda.messaging.error;

import org.bitbucket.openkilda.messaging.Message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents error message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "payload",
        "timestamp",
        "correlation-id"})
public class ErrorMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the error message.
     */
    @JsonProperty("payload")
    private ErrorData data;

    /**
     * Default constructor.
     */
    public ErrorMessage() {
    }

    /**
     * Instance constructor.
     *
     * @param data          error message payload
     * @param timestamp     timestamp value
     * @param correlationId request correlation id
     */
    @JsonCreator
    public ErrorMessage(@JsonProperty("payload") final ErrorData data,
                        @JsonProperty("timestamp") final long timestamp,
                        @JsonProperty("correlation-id") final String correlationId) {
        setData(data);
        setTimestamp(timestamp);
        setCorrelationId(correlationId);
    }

    /**
     * Returns payload of the error message.
     *
     * @return error message payload
     */
    @JsonProperty("payload")
    public ErrorData getData() {
        return data;
    }

    /**
     * Sets payload of the error message.
     *
     * @param data error message payload
     */
    @JsonProperty("payload")
    public void setData(final ErrorData data) {
        this.data = data;
    }
}
