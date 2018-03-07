package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class BandwidthJsonDeserializer extends JsonDeserializer<Bandwidth> {

    @Override
    public Bandwidth deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException, JsonProcessingException {
        Integer value = jsonParser.readValueAs(Integer.class);
        Bandwidth result = null;
        if (value != null) {
            result = new Bandwidth(value);
        }
        return result;
    }
}
