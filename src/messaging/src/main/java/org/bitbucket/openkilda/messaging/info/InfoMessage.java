package org.bitbucket.openkilda.messaging.info;

import org.bitbucket.openkilda.messaging.Message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents information message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "payload",
        "timestamp",
        "correlation-id"})
public class InfoMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the information message.
     */
    @JsonProperty("payload")
    private InfoData data;

    /**
     * Default constructor.
     */
    public InfoMessage() {
    }

    /**
     * Instance constructor.
     *
     * @param   data           info message payload
     * @param   timestamp      timestamp value
     * @param   correlationId  request correlation id
     */
    @JsonCreator
    public InfoMessage(@JsonProperty("payload") final InfoData data,
                       @JsonProperty("timestamp") final long timestamp,
                       @JsonProperty("correlation-id") final String correlationId) {
        setData(data);
        setTimestamp(timestamp);
        setCorrelationId(correlationId);
    }

    /**
     * Returns payload of the information message.
     *
     * @return  information message payload
     */
    @JsonProperty("payload")
    public InfoData getData() {
        return data;
    }

    /**
     * Sets payload of the information message.
     *
     * @param   data  information message payload
     */
    @JsonProperty("payload")
    public void setData(final InfoData data) {
        this.data = data;
    }
}
