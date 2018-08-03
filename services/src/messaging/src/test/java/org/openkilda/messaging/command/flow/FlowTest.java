package org.openkilda.messaging.command.flow;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.model.Flow;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class FlowTest {
    @Test
    public void sterilisationRoundTripTest() throws IOException {
        Flow source = new Flow(
                "flowId", 100L, true, 200, "description", "now", "source-switch", "dest-switch", 1, 2, 30, 40, 0, 50,
                null, null);

        String encoded = MAPPER.writeValueAsString(source);
        Flow decoded = MAPPER.readValue(encoded, Flow.class);

        Assert.assertEquals("Flow object have been mangled in serialisation/deserialization loop", source, decoded);
    }
}
