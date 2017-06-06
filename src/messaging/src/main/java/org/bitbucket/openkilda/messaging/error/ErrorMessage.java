package org.bitbucket.openkilda.messaging.error;

import static com.google.common.base.Objects.toStringHelper;
import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.TIMESTAMP;

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
        TIMESTAMP,
        CORRELATION_ID})
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
     * Instance constructor.
     *
     * @param data          error message payload
     * @param timestamp     timestamp value
     * @param correlationId request correlation id
     */
    @JsonCreator
    public ErrorMessage(@JsonProperty("payload") final ErrorData data,
                        @JsonProperty(TIMESTAMP) final long timestamp,
                        @JsonProperty(CORRELATION_ID) final String correlationId) {
        super(timestamp, correlationId);
        setData(data);
    }

    /**
     * Returns payload of the error message.
     *
     * @return error message payload
     */
    public ErrorData getData() {
        return data;
    }

    /**
     * Sets payload of the error message.
     *
     * @param data error message payload
     */
    public void setData(final ErrorData data) {
        this.data = data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add(CORRELATION_ID, correlationId)
                .add("payload", data)
                .toString();
    }
}
