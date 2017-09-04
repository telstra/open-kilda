package org.bitbucket.openkilda.floodlight.message.command;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.bitbucket.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.bitbucket.openkilda.messaging.command.flow.DefaultFlowsCommandData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

public class MessageTest {
    private static final String CORRELATION_ID = "f37530b6-02f1-493e-b28b-2d98a48cf4cc";
    private static final long TIMESTAMP = 23478952134L;
    private static final String DISCOVER_ISL = "{\"type\":\"COMMAND\",\"destination\":\"CONTROLLER\","
            + "\"payload\":{\"command\":\"discover_isl\","
            + "\"switch_id\":\"0000000000000001\",\"port_no\":1},\"timestamp\""
            + ":23478952134,\"correlation_id\":\"f37530b6-02f1-493e-b28b-2d98a48cf4cc\"}";
    private static final String DEFAULT_FLOWS = "{\"type\":\"COMMAND\","
            + "\"payload\":{\"command\":\"install_default_flows\","
            + "\"switch_id\":\"0x0000000000000001\"},"
            + "\"timestamp\":23478952134,\"correlation_id\":\"f37530b6-02f1-493e-b28b-2d98a48cf4cc\"}";
    private static final String DISCOVER_PATH = "{\"type\":\"COMMAND\",\"destination\":\"CONTROLLER\","
            + "\"payload\":{\"command\":\"discover_path\","
            + "\"source_switch_id\":\"0x0000000000000001\",\"source_port_no\":1,"
            + "\"destination_switch_id\":\"0x0000000000000002\"},"
            + "\"timestamp\":23478952134,\"correlation_id\":\"f37530b6-02f1-493e-b28b-2d98a48cf4cc\"}";
    private static final String ISL_INFO = "{\"type\":\"INFO\","
            + "\"payload\":{\"message_type\":\"isl\",\"id\":\"1_20\",\"latency_ns\":1123,"
            + "\"path\":[{\"switch_id\":\"1\",\"port_no\":20,\"seq_id\":0,"
            + "\"segment_latency\":1123},{\"switch_id\":\"2\",\"port_no\":1,"
            + "\"seq_id\":1}],\"speed\":0},"
            + "\"timestamp\":23478952134,\"correlation_id\":\"f37530b6-02f1-493e-b28b-2d98a48cf4cc\"}";
    private static final String PATH_INFO = "{\"type\":\"INFO\","
            + "\"payload\":{\"message_type\":\"path\",\"latency_ns\":1123,"
            + "\"path\":[{\"switch_id\":\"1\",\"port_no\":20,\"seq_id\":0,"
            + "\"segment_latency\":1123},{\"switch_id\":\"2\",\"port_no\":1,"
            + "\"seq_id\":1}]},"
            + "\"timestamp\":23478952134,\"correlation_id\":\"f37530b6-02f1-493e-b28b-2d98a48cf4cc\"}";
    private static final String SWITCH_INFO = "{\"type\":\"INFO\","
            + "\"payload\":{\"message_type\":\"switch\",\"switch_id\":"
            + "\"0000000000000001\",\"state\":\"ADDED\"},"
            + "\"timestamp\":23478952134,\"correlation_id\":\"f37530b6-02f1-493e-b28b-2d98a48cf4cc\"}";
    private static final String PORT_INFO = "{\"type\":\"INFO\","
            + "\"payload\":{\"message_type\":\"port\",\"switch_id\":"
            + "\"0000000000000001\",\"port_no\":1,\"max_capacity\":1000,\"state\":"
            + "\"ADD\"},"
            + "\"timestamp\":23478952134,\"correlation_id\":\"f37530b6-02f1-493e-b28b-2d98a48cf4cc\"}";
    private ObjectMapper mapper;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        mapper = new ObjectMapper();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSerializer() throws JsonProcessingException {
        CommandData data = new DiscoverIslCommandData("0000000000000001", 1);
        Message message = new CommandMessage(data, TIMESTAMP, CORRELATION_ID, Destination.CONTROLLER);
        String json = mapper.writeValueAsString(message);
        assertEquals(DISCOVER_ISL, json);
    }

    @Test
    public void testDeserialized() throws IOException {
        testDeserializer(DISCOVER_ISL, Message.class, CommandMessage.class, DiscoverIslCommandData.class);
        testDeserializer(DEFAULT_FLOWS, Message.class, CommandMessage.class, DefaultFlowsCommandData.class);
        testDeserializer(DISCOVER_PATH, Message.class, CommandMessage.class, DiscoverPathCommandData.class);
        testDeserializer(ISL_INFO, Message.class, InfoMessage.class, IslInfoData.class);
        testDeserializer(PATH_INFO, Message.class, InfoMessage.class, PathInfoData.class);
        testDeserializer(SWITCH_INFO, Message.class, InfoMessage.class, SwitchInfoData.class);
        testDeserializer(PORT_INFO, Message.class, InfoMessage.class, PortInfoData.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void testDeserializer(String testJson, Class mapperClass, Class targetClass, Class dataClass) throws IOException {
        Message message = (Message) mapper.readValue(testJson, mapperClass);
        String json = mapper.writeValueAsString(message);
        System.out.println(testJson);
        System.out.println(json);
        assertThat(message, instanceOf(targetClass));
        //assertThat(message.getData(), instanceOf(dataClass));
        assertEquals(testJson, json);
    }
}
