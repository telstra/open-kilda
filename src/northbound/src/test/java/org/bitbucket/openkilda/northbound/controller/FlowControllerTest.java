package org.bitbucket.openkilda.northbound.controller;

import static org.bitbucket.openkilda.northbound.controller.WorkFlowManagerKafkaMock.FLOW_ID;
import static org.bitbucket.openkilda.northbound.controller.WorkFlowManagerKafkaMock.flow;
import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;
import static org.bitbucket.openkilda.northbound.utils.Constants.DEFAULT_CORRELATION_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bitbucket.openkilda.northbound.model.HealthCheck;

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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles({"test"})
public class FlowControllerTest {
    private static final String SENDER_TOPIC = "kilda.nb.wfm";
    private static final String RECEIVER_TOPIC = "kilda.wfm.nb";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ContainerProperties containerProperties = new ContainerProperties(SENDER_TOPIC);
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
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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
    public void createFlow() throws Exception {
        MvcResult result = mockMvc.perform(put("/flows")
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE)
                .content(mapper.writeValueAsString(flow)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }

    @Test
    public void getFlow() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/{flow-id}", FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }

    @Test
    public void deleteFlow() throws Exception {
        MvcResult result = mockMvc.perform(delete("/flows/{flow-id}", FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }

    @Test
    public void updateFlow() throws Exception {
        MvcResult result = mockMvc.perform(put("/flows/{flow-id}", FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE)
                .content(mapper.writeValueAsString(flow)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }

    @Test
    public void getFlows() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows", FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }

    @Test
    public void statusFlow() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/status/{flow-id}", FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }

    @Test
    public void statusFlows() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/status")
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .param("status", "INSTALLATION")
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }

    @Test
    public void pathFlow() throws Exception {
        MvcResult result = mockMvc.perform(get("/flows/path/{flow-id}", FLOW_ID)
                .header(CORRELATION_ID, DEFAULT_CORRELATION_ID)
                .contentType(APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
    }
}
