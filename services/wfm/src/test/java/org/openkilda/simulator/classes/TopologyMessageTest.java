package org.openkilda.simulator.classes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openkilda.simulator.messages.TopologyMessage;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class TopologyMessageTest {
    private ObjectMapper mapper = new ObjectMapper();

    public static final String GOOD_JSON="{\"type\":\"TOPOLOGY\",\"switches\":[{\"dpid\":\"00:00:00:00:00:01\"," +
            "\"num_of_ports\":1,\"links\":[{\"latency\":10,\"local_port\":1,\"peer_switch\":\"00:00:00:00:02\"," +
            "\"peer_port\":1}]},{\"dpid\":\"00:00:00:00:00:02\",\"num_of_ports\":1,\"links\":[{\"latency\":10," +
            "\"local_port\":1,\"peer_switch\":\"00:00:00:00:01\",\"peer_port\":1}]}]}";

    private final String BAD_JSON="{\"type\": \"FRED\",\"switches\": [{\"dpid\": \"00:00:00:00:00:01\"," +
            "\"num_of_ports\": 1,\"links\": [{\"latency\": 10,\"local_port\": 1,\"peer_switch\": \"00:00:00:00:02\"," +
            "\"peer_port\": 1}]},{\"dpid\": \"00:00:00:00:00:02\",\"num_of_ports\": 1,\"links\": [{\"latency\": 10," +
            "\"local_port\": 1,\"peer_switch\": \"00:00:00:00:01\",\"peer_port\": 1}]}]}";
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void jsonToObjectTest() throws Exception {
        Object object = mapper.readValue(GOOD_JSON, TopologyMessage.class);

        assertThat(object, instanceOf(TopologyMessage.class));
        assertEquals(GOOD_JSON, mapper.writeValueAsString(object));

        thrown.expect(IOException.class);
        object = mapper.readValue(BAD_JSON, TopologyMessage.class);
    }
}