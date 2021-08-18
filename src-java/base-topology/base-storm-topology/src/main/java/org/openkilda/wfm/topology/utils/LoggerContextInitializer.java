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
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.TOPOLOGY_NAME;
import static org.openkilda.wfm.protocol.BoltToBoltMessage.FIELD_ID_CORRELATION_ID;
import static org.openkilda.wfm.protocol.JsonMessage.FIELD_ID_JSON;

import org.openkilda.messaging.Message;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An aspect for IRichBolt / IStatefulBolt which decorates processing of a tuple with the logger context.
 */
@Aspect
public class LoggerContextInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerContextInitializer.class);

    private String stormId;

    public LoggerContextInitializer() {
        this.stormId = getCleanTopoName((String) System.getProperties().get("storm.id"));
    }

    static String getCleanTopoName(String stormId) {
        if (stormId == null || stormId.isEmpty()) {
            return "";
        }
        int matches = StringUtils.countMatches(stormId, "-");
        if (matches < 2) {
            return stormId;
        }
        matches -= 1;

        int idx = StringUtils.ordinalIndexOf(stormId, "-", matches);
        return stormId.substring(0, idx);
    }

    /**
     * Wraps "execute" method of storm bolts to inject/cleanup fields in logger MDC.
     */
    @Around("execution(* org.apache.storm.topology.IRichBolt+.execute(org.apache.storm.tuple.Tuple)) && args(input)"
            + "|| execution(* org.apache.storm.topology.IStatefulBolt+.execute(org.apache.storm.tuple.Tuple)) "
            + "&& args(input)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint, Tuple input) throws Throwable {
        CommandContext context = extract(input)
                .orElseGet(() -> {
                    LOGGER.debug("CorrelationId was not sent or can't be extracted for tuple {}", input);
                    return new CommandContext(DEFAULT_CORRELATION_ID);
                });

        Map<String, String> fields = prepareFields(context);
        fields.put(TOPOLOGY_NAME, stormId);

        Map<String, String> current = inject(fields);

        try {
            return joinPoint.proceed(joinPoint.getArgs());
        } finally {
            revert(current);
        }
    }

    /**
     * Extract the context information (correlation id) from a tuple.
     *
     * @param input a tuple to process
     * @return the context if required information is found
     */
    public static Optional<CommandContext> extract(Tuple input) {
        final Fields fields = input.getFields();
        if (fields == null || fields.size() == 0) {
            return Optional.empty();
        }

        if (fields.contains(AbstractBolt.FIELD_ID_CONTEXT)) {
            Object context = input.getValueByField(AbstractBolt.FIELD_ID_CONTEXT);
            if (context instanceof CommandContext) {
                return Optional.of((CommandContext) context);
            } else {
                return Optional.empty();
            }
        }

        if (fields.contains(FIELD_ID_CORRELATION_ID)) {
            Optional<CommandContext> context = Optional.ofNullable(input.getStringByField(FIELD_ID_CORRELATION_ID))
                    .map(String::trim)
                    .map(Strings::emptyToNull)
                    .map(CommandContext::new);
            if (context.isPresent()) {
                return context;
            }
        }

        if (fields.contains(KafkaRecordTranslator.FIELD_ID_PAYLOAD)) {
            Object messageField = input.getValueByField(KafkaRecordTranslator.FIELD_ID_PAYLOAD);
            if (messageField instanceof Message) {
                Optional<CommandContext> context = Optional.ofNullable(((Message) messageField).getCorrelationId())
                        .map(String::trim)
                        .map(Strings::emptyToNull)
                        .map(CommandContext::new);
                if (context.isPresent()) {
                    return context;
                }
            } else if (messageField instanceof String) {
                String corrId = exractFromJson((String) messageField);
                if (corrId != null) {
                    return Optional.of(new CommandContext(corrId));
                }
            }
        }

        if (fields.contains(FIELD_ID_JSON)) {
            String corrId = exractFromJson(input.getStringByField(FIELD_ID_JSON));
            if (corrId != null) {
                return Optional.of(new CommandContext(corrId));
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

    private static Map<String, String> prepareFields(CommandContext context) {
        Map<String, String> fields = new HashMap<>();
        fields.put(CORRELATION_ID, context.getCorrelationId());
        if (context.getKafkaTopic() != null) {
            fields.put("kafka.partition", String.format("%s-%s", context.getKafkaTopic(), context.getKafkaPartition()));
            fields.put("kafka.offset", context.getKafkaOffset().toString());
        }
        return fields;
    }

    private static Map<String, String> inject(Map<String, String> fields) {
        Map<String, String> current = new HashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            current.put(key, MDC.get(key));
            MDC.put(key, entry.getValue());
        }
        return current;
    }

    private static void revert(Map<String, String> fields) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                MDC.remove(entry.getKey());
            } else {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
