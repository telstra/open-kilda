/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.wfm.protocol.BoltToBoltMessage.FIELD_ID_CORRELATION_ID;
import static org.openkilda.wfm.protocol.JsonMessage.FIELD_ID_JSON;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.openkilda.messaging.Message;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Represents a correlation context for processing of a tuple.
 */
public final class CorrelationContext {
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
            Object messageField = input.getValueByField(MESSAGE_FIELD);
            if (messageField instanceof Message) {
                Optional<CorrelationContext> result = Optional.ofNullable(((Message) messageField).getCorrelationId())
                        .map(String::trim)
                        .map(Strings::emptyToNull)
                        .map(CorrelationContext::new);
                if (result.isPresent()) {
                    return result;
                }
            } else if (messageField instanceof String) {
                String corrId = exractFromJson((String) messageField);
                if (corrId != null) {
                    return Optional.of(new CorrelationContext(corrId));
                }
            }
        }

        if (fields.contains(FIELD_ID_JSON)) {
            String corrId = exractFromJson(input.getStringByField(FIELD_ID_JSON));
            if (corrId != null) {
                return Optional.of(new CorrelationContext(corrId));
            }
        }

        return Optional.empty();
    }

    private static String exractFromJson(String jsonStr) {
        JsonParser jsonParser = new JsonParser();
        try {
            JsonElement json = jsonParser.parse(jsonStr);
            if (json.isJsonObject()) {
                JsonPrimitive corrIdObj = ((JsonObject) json).getAsJsonPrimitive(CORRELATION_ID);
                if (corrIdObj != null && corrIdObj.isString()) {
                    return corrIdObj.getAsString();
                }
            }
        } catch (JsonParseException ex) {
            LOGGER.warn("Unable to parse message payload as a JSON object.", ex);
        }
        return null;
    }

    private CorrelationContext(String id) {
        this.id = id;
    }
}
