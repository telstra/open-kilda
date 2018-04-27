package org.openkilda.wfm.topology.utils;

import com.google.common.base.Strings;
import com.google.gson.*;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.wfm.protocol.BoltToBoltMessage.FIELD_ID_CORRELATION_ID;
import static org.openkilda.wfm.protocol.JsonMessage.FIELD_ID_JSON;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

/**
 * Represents a correlation context for processing of a tuple.
 */
public class CorrelationContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationContext.class);

    private String id;

    public String getId() {
        return id;
    }

    /**
     * Extract the context information (correlation id) from a tuple.
     *
     * @param input a tuple to process
     * @return the context if required information is found
     */
    public static Optional<CorrelationContext> extractFrom(Tuple input) {
        final Fields fields = input.getFields();
        if (fields == null || fields.size() == 0) {
            return Optional.empty();
        }

        if (fields.contains(FIELD_ID_CORRELATION_ID)) {
            Optional<CorrelationContext> result = Optional.ofNullable(input.getStringByField(FIELD_ID_CORRELATION_ID))
                    .map(String::trim)
                    .map(Strings::emptyToNull)
                    .map(CorrelationContext::new);
            if (result.isPresent()) {
                return result;
            }
        }

        if (fields.contains(MESSAGE_FIELD)) {
            Optional<CorrelationContext> result = Optional.ofNullable(input.getValueByField(MESSAGE_FIELD))
                    .map(item -> item instanceof Message ? ((Message) item).getCorrelationId() : null)
                    .map(String::trim)
                    .map(Strings::emptyToNull)
                    .map(CorrelationContext::new);
            if (result.isPresent()) {
                return result;
            }
        }

        if (fields.contains(FIELD_ID_JSON)) {
            JsonParser jsonParser = new JsonParser();
            try {
                JsonElement json = jsonParser.parse(input.getStringByField(FIELD_ID_JSON));
                if (json.isJsonObject()) {
                    JsonPrimitive corrIdObj = ((JsonObject) json).getAsJsonPrimitive(CORRELATION_ID);
                    if (corrIdObj.isString()) {
                        return Optional.of(new CorrelationContext(corrIdObj.getAsString()));
                    }
                }
            } catch (JsonParseException ex) {
                LOGGER.warn("Unable to parse message payload as a JSON object.", ex);
            }
        }

        return Optional.empty();
    }

    private CorrelationContext(String id) {
        this.id = id;
    }
}
