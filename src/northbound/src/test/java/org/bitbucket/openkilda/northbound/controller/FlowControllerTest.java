package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;
import static org.bitbucket.openkilda.northbound.utils.Constants.DEFAULT_CORRELATION_ID;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.payload.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowPathResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsStatusResponsePayload;
import org.bitbucket.openkilda.northbound.model.HealthCheck;
import org.bitbucket.openkilda.northbound.model.NorthboundError;
import org.bitbucket.openkilda.northbound.utils.NorthboundException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:northbound.properties")
public class FlowControllerTest {
    private static final String USERNAME = "kilda";
    private static final String PASSWORD = "kilda";
    private static final String ROLE = "ADMIN";
    private static final String SENDER_TOPIC = "kilda.nb.wfm";
    private static final String RECEIVER_TOPIC = "kilda.wfm.nb";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ContainerProperties containerProperties = new ContainerProperties(SENDER_TOPIC);
    private static final NorthboundError AUTH_ERROR = new NorthboundError(DEFAULT_CORRELATION_ID, 0,
            HttpStatus.UNAUTHORIZED, ErrorType.AUTH_FAILED.toString(), "InsufficientAuthenticationException");
    private static final NorthboundError NOT_FOUND_ERROR = new NorthboundError(DEFAULT_CORRELATION_ID, 0,
            HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND.toString(), NorthboundException.class.getSimpleName());
    private final Map<String, Object> consumerProperties =
            KafkaTestUtils.consumerProps("test", "true", embeddedKafka);
    private final DefaultKafkaConsumerFactory<String, String> consumerFactory =
            new DefaultKafkaConsumerFactory<>(consumerProperties);
    private final KafkaMessageListenerContainer<String, String> container =
            new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

    private MockMvc mockMvc;

    @Value("${kafka.groupid}")
    private String groupId;

    @Autowired
    HealthCheck healthCheck;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @ClassRule
    public static KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, SENDER_TOPIC, RECEIVER_TOPIC);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        String kafkaBootstrapServers = embeddedKafka.getBrokersAsString();
        System.setProperty("kafka.bootstrap-servers", kafkaBootstrapServers);
        embeddedKafka.before();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        embeddedKafka.after();
    }

    @Before
    public void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        container.setupMessageListener(new MessageListener<String, String>() {
            @Override
            public void onMessage(ConsumerRecord<String, String> record) {
            }
        });
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    @After
    public void tearDown() throws InterruptedException {
        container.stop();
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void createFlow() throws Exception {
        MvcResult result = mockMvc.perform(put("/flows")
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE)
                .content(mapper.writeValueAsString(WorkFlowManagerKafkaMock.flow)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowPayload response = mapper.readValue(result.getResponse().getContentAsString(), FlowPayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flow, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void getFlow() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/{flow-id}", WorkFlowManagerKafkaMock.FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowPayload response = mapper.readValue(result.getResponse().getContentAsString(), FlowPayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flow, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void deleteFlow() throws Exception {
        MvcResult result = mockMvc.perform(delete("/flows/{flow-id}", WorkFlowManagerKafkaMock.FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowPayload response = mapper.readValue(result.getResponse().getContentAsString(), FlowPayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flow, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void updateFlow() throws Exception {
        MvcResult result = mockMvc.perform(put("/flows/{flow-id}", WorkFlowManagerKafkaMock.FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE)
                .content(mapper.writeValueAsString(WorkFlowManagerKafkaMock.flow)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowPayload response = mapper.readValue(result.getResponse().getContentAsString(), FlowPayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flow, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void getFlows() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows", WorkFlowManagerKafkaMock.FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowsResponsePayload response =
                mapper.readValue(result.getResponse().getContentAsString(), FlowsResponsePayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flows, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void statusFlow() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/status/{flow-id}", WorkFlowManagerKafkaMock.FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowStatusResponsePayload response =
                mapper.readValue(result.getResponse().getContentAsString(), FlowStatusResponsePayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flowStatus, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void statusFlows() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/status")
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .param("status", "INSTALLATION")
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowsStatusResponsePayload response =
                mapper.readValue(result.getResponse().getContentAsString(), FlowsStatusResponsePayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flowsStatus, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void pathFlow() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/path/{flow-id}", WorkFlowManagerKafkaMock.FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        FlowPathResponsePayload response =
                mapper.readValue(result.getResponse().getContentAsString(), FlowPathResponsePayload.class);
        assertEquals(WorkFlowManagerKafkaMock.flowPath, response);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void getNonExistingFlow() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/{flow-id}", WorkFlowManagerKafkaMock.ERROR_FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        NorthboundError response = mapper.readValue(result.getResponse().getContentAsString(), NorthboundError.class);
        assertEquals(NOT_FOUND_ERROR, response);
    }

    @Test
    public void emptyCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/path/{flow-id}", WorkFlowManagerKafkaMock.FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        NorthboundError response = mapper.readValue(result.getResponse().getContentAsString(), NorthboundError.class);
        assertEquals(AUTH_ERROR.getCorrelationId(), response.getCorrelationId());
        assertEquals(AUTH_ERROR.getDescription(), response.getDescription());
    }
}
