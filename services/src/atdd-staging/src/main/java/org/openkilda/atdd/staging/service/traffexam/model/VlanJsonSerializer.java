package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class VlanJsonSerializer extends JsonSerializer<Vlan> {

    @Override
    public void serialize(Vlan vlan, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (vlan == null) {
            jsonGenerator.writeNull();
        } else {
            jsonGenerator.writeNumber(vlan.getVlanTag());
        }
    }
}
