package org.openkilda.messaging.ctrl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;

import static org.openkilda.messaging.Utils.*;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        DESTINATION,
        PAYLOAD,
        TIMESTAMP,
        CORRELATION_ID})
public class CtrlResponse extends Message {
    private static final long serialVersionUID = 1L;

    @JsonProperty(PAYLOAD)
    private ResponseData data;

    @JsonCreator
    public CtrlResponse(@JsonProperty(PAYLOAD) ResponseData data,
                        @JsonProperty(TIMESTAMP) final long timestamp,
                        @JsonProperty(CORRELATION_ID) final String correlationId,
                        @JsonProperty(DESTINATION) final Destination destination) {
        super(timestamp, correlationId, destination);
        this.data = data;
    }

    public ResponseData getData() {
        return data;
    }
}
