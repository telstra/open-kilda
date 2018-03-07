package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class TimeLimitJsonSerializer extends JsonSerializer<TimeLimit> {

    @Override
    public void serialize(TimeLimit timeLimit, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (timeLimit == null) {
            jsonGenerator.writeNull();
        } else {
            jsonGenerator.writeNumber(timeLimit.getSeconds());
        }
    }
}
