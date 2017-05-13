package org.bitbucket.openkilda.messaging.command;

import org.bitbucket.openkilda.messaging.Message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents command message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "payload",
        "timestamp",
        "correlation_id"})
public class CommandMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the command message.
     */
    @JsonProperty("payload")
    private CommandData data;

    /**
     * Instance constructor.
     *
     * @param   data           command message payload
     * @param   timestamp      timestamp value
     * @param   correlationId  request correlation id
     */
    @JsonCreator
    public CommandMessage(@JsonProperty("payload") final CommandData data,
                          @JsonProperty("timestamp") final long timestamp,
                          @JsonProperty("correlation_id") final String correlationId) {
        super(timestamp, correlationId);
        setData(data);
    }

    /**
     * Returns payload of the command message.
     *
     * @return  command message payload
     */
    @JsonProperty("payload")
    public CommandData getData() {
        return data;
    }

    /**
     * Sets payload of the command message.
     *
     * @param   data  command message payload
     */
    @JsonProperty("payload")
    public void setData(final CommandData data) {
        this.data = data;
    }
}
