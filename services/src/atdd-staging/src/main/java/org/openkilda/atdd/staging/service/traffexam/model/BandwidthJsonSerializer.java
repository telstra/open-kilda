package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class BandwidthJsonSerializer extends JsonSerializer<Bandwidth> {

    @Override
    public void serialize(Bandwidth bandwidth, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (bandwidth == null) {
            jsonGenerator.writeNull();
        } else {
            jsonGenerator.writeNumber(bandwidth.getKbps());
        }
    }
}
