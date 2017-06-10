package org.bitbucket.openkilda.messaging.info;

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
 * Class represents information message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        DESTINATION,
        PAYLOAD,
        TIMESTAMP,
        CORRELATION_ID})
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
     * Instance constructor.
     *
     * @param data          info message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    @JsonCreator
    public InfoMessage(@JsonProperty(PAYLOAD) final InfoData data,
                       @JsonProperty(TIMESTAMP) final long timestamp,
                       @JsonProperty(CORRELATION_ID) final String correlationId,
                       @JsonProperty(DESTINATION) final Destination destination) {
        super(timestamp, correlationId, destination);
        setData(data);
    }

    /**
     * Returns payload of the information message.
     *
     * @return information message payload
     */
    public InfoData getData() {
        return data;
    }

    /**
     * Sets payload of the information message.
     *
     * @param data information message payload
     */
    public void setData(final InfoData data) {
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
