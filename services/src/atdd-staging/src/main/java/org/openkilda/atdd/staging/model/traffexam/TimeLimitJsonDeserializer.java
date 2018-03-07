package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class TimeLimitJsonDeserializer extends JsonDeserializer<TimeLimit> {

    @Override
    public TimeLimit deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException, JsonProcessingException {
        Long value = jsonParser.readValueAs(Long.class);
        TimeLimit result = null;
        if (value != null) {
            result = new TimeLimit(value);
        }
        return result;
    }
}
