package org.openkilda.wfm.topology.opentsdb;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.topology.Topology;

import java.io.File;
import java.util.Collections;

public class OpenTSDBTopologyTest extends AbstractStormTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOPIC = "opentsdb-topic";
    private static final long timestamp = System.currentTimeMillis();
    private static ClientAndServer mockServer;

    @BeforeClass
    public static void setupOnce() throws Exception {
        mockServer = startClientAndServer(4242);
        mockServer.when(HttpRequest
                .request()
                .withMethod("POST")
                .withPath("api/put"))
            .respond(HttpResponse.response());

        AbstractStormTest.setupOnce();
        OpenTSDBTopology topology = new OpenTSDBTopology(makeLaunchEnvironment());
        StormTopology stormTopology = topology.createTopology();
        Config config = stormConfig();
        config.setMaxTaskParallelism(1);
        cluster.submitTopology(OpenTSDBTopologyTest.class.getSimpleName(), config, stormTopology);

        //todo: should be replaced with more suitable wat
        Utils.sleep(10000);
    }

    @Before
    public void init() {
        mockServer.reset();
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        cluster.killTopology(OpenTSDBTopologyTest.class.getSimpleName());
        Utils.sleep(2000);
        AbstractStormTest.teardownOnce();
        mockServer.close();
    }

    @Test
    public void shouldSuccessfulSendDatapoint() throws Exception {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);
        InfoMessage message = new InfoMessage(datapoint, timestamp, "correlationId");

        kProducer.pushMessage(TOPIC, objectMapper.writeValueAsString(message));
        Thread.sleep(2000);
        mockServer.verify(HttpRequest.request(),
                VerificationTimes.exactly(1));
    }

    @Test
    public void shouldNotSendDatapointRequests() throws Exception {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);
        InfoMessage message = new InfoMessage(datapoint, timestamp, "correlationId");

        kProducer.pushMessage(TOPIC, objectMapper.writeValueAsString(message));
        kProducer.pushMessage(TOPIC, objectMapper.writeValueAsString(message));
        Thread.sleep(2000);
        mockServer.verifyZeroInteractions();
    }

}
