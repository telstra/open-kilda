package org.bitbucket.openkilda.floodlight.message.command;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bitbucket.openkilda.floodlight.message.CommandMessage;
import org.bitbucket.openkilda.floodlight.message.InfoMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.command.CommandData.Destination;
import org.bitbucket.openkilda.floodlight.message.info.IslInfoData;
import org.bitbucket.openkilda.floodlight.message.info.PathInfoData;
import org.bitbucket.openkilda.floodlight.message.info.PortInfoData;
import org.bitbucket.openkilda.floodlight.message.info.SwitchInfoData;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MessageTest {
  private ObjectMapper mapper;

  private static final String DISCOVER_ISL = "{\"type\":\"COMMAND\",\"timestamp\""
      + ":23478952134,\"data\":{\"command\":\"discover_isl\",\"destination\":"
      + "\"CONTROLLER\",\"switch_id\":\"0000000000000001\",\"port_no\":1}}";
  
  private static final String DEFAULT_FLOWS = "{\"type\":\"COMMAND\","
      + "\"timestamp\":23478952134,\"data\":{\"command\":"
      + "\"install_default_flows\",\"destination\":\"CONTROLLER\","
      + "\"switch_id\":\"0x0000000000000001\"}}";
  
  private static final String DISCOVER_PATH = "{\"type\":\"COMMAND\","
      + "\"timestamp\":23478952134,\"data\":{\"command\":"
      + "\"discover_path\",\"destination\":\"CONTROLLER\","
      + "\"source_switch_id\":\"0x0000000000000001\",\"source_port_no\":1,"
      + "\"destination_switch_id\":\"0x0000000000000002\"}}";

  private static final String ISL_INFO = "{\"type\":\"INFO\",\"timestamp\":"
      + "23478952134,\"data\":{\"message_type\":\"isl\",\"latency_ns\":1123,"
      + "\"path\":[{\"switch_id\":\"1\",\"port_no\":20,\"seq_id\":0,"
      + "\"segment_latency\":1123},{\"switch_id\":\"2\",\"port_no\":1,"
      + "\"seq_id\":1}]}}";
  
  private static final String PATH_INFO = "{\"type\":\"INFO\",\"timestamp\":"
      + "23478952134,\"data\":{\"message_type\":\"path\",\"latency_ns\":1123,"
      + "\"path\":[{\"switch_id\":\"1\",\"port_no\":20,\"seq_id\":0,"
      + "\"segment_latency\":1123},{\"switch_id\":\"2\",\"port_no\":1,"
      + "\"seq_id\":1}]}}";
  
  private static final String SWITCH_INFO = "{\"type\":\"INFO\",\"timestamp\""
      + ":23478952134,\"data\":{\"message_type\":\"switch\",\"switch_id\":"
      + "\"0000000000000001\",\"state\":\"ADDED\"}}";
  
  private static final String PORT_INFO = "{\"type\":\"INFO\",\"timestamp\":"
      + "23478952134,\"data\":{\"message_type\":\"port\",\"switch_id\":"
      + "\"0000000000000001\",\"port_no\":1,\"max_capacity\":1000,\"state\":"
      + "\"ADD\"}}";
  
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
  public void testSerializer() {
    CommandData data = new DiscoverISLCommandData().withSwitchId("0000000000000001").withPortNo(1)
        .withDestination(Destination.CONTROLLER);

    Message message = new CommandMessage().withData(data).withTimestamp(23478952134L);

    String json = null;
    try {
      json = mapper.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    assertEquals(json, DISCOVER_ISL);
  }
  
  @Test
  public void testDeserialized() {
    testDeserializer(DISCOVER_ISL, Message.class, CommandMessage.class, DiscoverISLCommandData.class);
    testDeserializer(DEFAULT_FLOWS, Message.class, CommandMessage.class, DefaultFlowsCommandData.class);
    testDeserializer(DISCOVER_PATH, Message.class, CommandMessage.class, DiscoverPathCommandData.class);
    testDeserializer(ISL_INFO, Message.class, InfoMessage.class, IslInfoData.class);
    testDeserializer(PATH_INFO, Message.class, InfoMessage.class, PathInfoData.class);
    testDeserializer(SWITCH_INFO, Message.class, InfoMessage.class, SwitchInfoData.class);
    testDeserializer(PORT_INFO, Message.class, InfoMessage.class, PortInfoData.class);
  }
  
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void testDeserializer(String testJson, Class mapperClass, Class targetClass, Class dataClass){
    Message message = null;
    String json = null;
    try {
      message = (Message) mapper.readValue(testJson, mapperClass);
      json = mapper.writeValueAsString(message);
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.out.println(testJson);
    System.out.println(json);
    assertThat(message, instanceOf(targetClass));
//    assertThat(message.getData(), instanceOf(dataClass));
    assertEquals(testJson, json);
  }
}
