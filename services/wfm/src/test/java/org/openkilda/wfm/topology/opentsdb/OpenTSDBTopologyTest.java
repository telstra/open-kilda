package org.openkilda.wfm.topology.opentsdb;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.openkilda.messaging.Utils.MAPPER;

import org.apache.storm.Testing;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.testing.MockedSources;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.Topic;
import org.openkilda.wfm.StableAbstractStormTest;
import org.openkilda.wfm.topology.TestingKafkaBolt;

import java.util.Collections;
import java.util.Map;

public class OpenTSDBTopologyTest extends StableAbstractStormTest {
    private static final long timestamp = System.currentTimeMillis();
    private static ClientAndServer mockServer;

    @BeforeClass
    public static void setupOnce() throws Exception {
        StableAbstractStormTest.setupOnce();
        mockServer = startClientAndServer(4242);
        mockServer.when(HttpRequest
                .request()
                .withMethod("POST")
                .withPath("api/put"))
            .respond(HttpResponse.response());
    }

    @Before
    public void init() {
        mockServer.reset();
    }

    @Test
    public void shouldSuccessfulSendDatapoint() throws Exception {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);

        MockedSources sources = new MockedSources();
        // TODO: rather than use Topic.OTSDB, grab it from the TopologyConfig object (which does
        // not exist at this point in the code.
        sources.addMockData(Topic.OTSDB+"-spout",
                new Values(MAPPER.writeValueAsString(datapoint)));
        completeTopologyParam.setMockedSources(sources);

        Testing.withTrackedCluster(clusterParam, (cluster) ->  {
            OpenTSDBTopology topology = new TestingTargetTopology(new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });

        //verify that request is sent to OpenTSDB server
        mockServer.verify(HttpRequest.request(), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldSendDatapointRequestsOnlyOnce() throws Exception {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);
        String jsonDatapoint = MAPPER.writeValueAsString(datapoint);

        MockedSources sources = new MockedSources();
        sources.addMockData(Topic.OTSDB+"-spout",
                new Values(jsonDatapoint), new Values(jsonDatapoint));
        completeTopologyParam.setMockedSources(sources);

        Testing.withTrackedCluster(clusterParam, (cluster) ->  {
            OpenTSDBTopology topology = new TestingTargetTopology(new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });
        //verify that request is sent to OpenTSDB server once
        mockServer.verify(HttpRequest.request(), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldSendDatapointRequestsTwice() throws Exception {
        Datapoint datapoint1 = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);
        String jsonDatapoint1 = MAPPER.writeValueAsString(datapoint1);

        Datapoint datapoint2 = new Datapoint("metric", timestamp, Collections.emptyMap(), 456);
        String jsonDatapoint2 = MAPPER.writeValueAsString(datapoint2);

        MockedSources sources = new MockedSources();
        sources.addMockData(Topic.OTSDB+"-spout",
                new Values(jsonDatapoint1), new Values(jsonDatapoint2));
        completeTopologyParam.setMockedSources(sources);

        Testing.withTrackedCluster(clusterParam, (cluster) ->  {
            OpenTSDBTopology topology = new TestingTargetTopology(new TestingKafkaBolt());
            StormTopology stormTopology = topology.createTopology();

            Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });
        //verify that request is sent to OpenTSDB server once
        mockServer.verify(HttpRequest.request(), VerificationTimes.exactly(2));
    }

    private class TestingTargetTopology extends OpenTSDBTopology {

        private KafkaBolt kafkaBolt;

        TestingTargetTopology(KafkaBolt kafkaBolt) throws Exception {
            super(makeLaunchEnvironment());

            this.kafkaBolt = kafkaBolt;
        }

        @Override
        protected void checkAndCreateTopic(String topic) {
        }

        @Override
        protected void createHealthCheckHandler(TopologyBuilder builder, String prefix) {
        }

        @Override
        public String makeTopologyName() {
            return OpenTSDBTopology.class.getSimpleName().toLowerCase();
        }

        @Override
        protected KafkaBolt createKafkaBolt(String topic) {
            return kafkaBolt;
        }

    }

}
