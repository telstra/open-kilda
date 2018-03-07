package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class VlanJsonDeserializer extends JsonDeserializer<Vlan> {

    @Override
    public Vlan deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException, JsonProcessingException {
        Integer value = jsonParser.readValueAs(Integer.class);
        Vlan result = null;
        if (value != null) {
            result = new Vlan(value);
        }
        return result;
    }
}
