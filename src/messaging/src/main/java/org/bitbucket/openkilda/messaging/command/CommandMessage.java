package org.bitbucket.openkilda.messaging.command;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.DESTINATION;
import static org.bitbucket.openkilda.messaging.Utils.PAYLOAD;
import static org.bitbucket.openkilda.messaging.Utils.TIMESTAMP;

import org.bitbucket.openkilda.messaging.Destination;
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
        DESTINATION,
        PAYLOAD,
        TIMESTAMP,
        CORRELATION_ID})
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
     * @param data          command message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    @JsonCreator
    public CommandMessage(@JsonProperty(PAYLOAD) final CommandData data,
                          @JsonProperty(TIMESTAMP) final long timestamp,
                          @JsonProperty(CORRELATION_ID) final String correlationId,
                          @JsonProperty(DESTINATION) final Destination destination) {
        super(timestamp, correlationId, destination);
        setData(data);
    }

    /**
     * Returns payload of the command message.
     *
     * @return command message payload
     */
    public CommandData getData() {
        return data;
    }

    /**
     * Sets payload of the command message.
     *
     * @param data command message payload
     */
    public void setData(final CommandData data) {
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
                .add(DESTINATION, destination)
                .add(PAYLOAD, data)
                .toString();
    }
}
