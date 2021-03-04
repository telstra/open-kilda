/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.opentsdb;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.openkilda.wfm.topology.opentsdb.OpenTsdbTopology.OTSDB_PARSE_BOLT_ID;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.bluegreen.Signal;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.StableAbstractStormTest;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.opentsdb.bolts.DatapointParseBolt;

import org.apache.storm.Testing;
import org.apache.storm.generated.Bolt;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.testing.MockedSources;
import org.apache.storm.tuple.Values;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class OpenTsdbTopologyTest extends StableAbstractStormTest {
    private static final long timestamp = System.currentTimeMillis();
    private static final int OPENTSDB_PORT = 4243;
    private static ClientAndServer mockServer;
    private static final HttpRequest REQUEST = HttpRequest.request().withMethod("POST").withPath("/api/put");

    @BeforeClass
    public static void setupOnce() throws Exception {
        StableAbstractStormTest.startCompleteTopology();

        mockServer = startClientAndServer(OPENTSDB_PORT);
    }

    @Before
    public void init() {
        mockServer.when(REQUEST)
                .respond(HttpResponse.response().withStatusCode(200));
    }

    @After
    public void cleanup() {
        mockServer.reset();
    }

    @Test
    public void shouldSuccessfulSendDatapoint() {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);

        MockedSources sources = new MockedSources();
        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            OpenTsdbTopology topology = new OpenTsdbTopology(makeLaunchEnvironment(getProperties()));

            sources.addMockData(ZooKeeperSpout.SPOUT_ID,
                    new Values(LifecycleEvent.builder().signal(Signal.NONE).build(), null));
            sources.addMockData(OpenTsdbTopology.OTSDB_SPOUT_ID,
                    new Values(null, datapoint));
            completeTopologyParam.setMockedSources(sources);

            StormTopology stormTopology = topology.createTopology();
            stormTopology.get_bolts().remove(ZooKeeperBolt.BOLT_ID);
            activateDatapointParserBolt(stormTopology);
            Map result = Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });

        //verify that request is sent to OpenTSDB server
        mockServer.verify(REQUEST, VerificationTimes.exactly(1));
    }

    @Test
    public void shouldSendDatapointRequestsOnlyOnce() throws Exception {
        Datapoint datapoint = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);

        MockedSources sources = new MockedSources();

        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            OpenTsdbTopology topology = new OpenTsdbTopology(makeLaunchEnvironment(getProperties()));

            sources.addMockData(ZooKeeperSpout.SPOUT_ID,
                    new Values(LifecycleEvent.builder().signal(Signal.NONE).build(), null));
            sources.addMockData(OpenTsdbTopology.OTSDB_SPOUT_ID,
                    new Values(null, datapoint), new Values(null, datapoint));
            completeTopologyParam.setMockedSources(sources);

            StormTopology stormTopology = topology.createTopology();
            stormTopology.get_bolts().remove(ZooKeeperBolt.BOLT_ID);
            activateDatapointParserBolt(stormTopology);

            Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });
        //verify that request is sent to OpenTSDB server once
        mockServer.verify(REQUEST, VerificationTimes.exactly(1));
    }

    @Test
    public void shouldSendDatapointRequestsTwice() throws Exception {
        Datapoint datapoint1 = new Datapoint("metric", timestamp, Collections.emptyMap(), 123);
        Datapoint datapoint2 = new Datapoint("metric", timestamp, Collections.emptyMap(), 456);

        MockedSources sources = new MockedSources();

        Testing.withTrackedCluster(clusterParam, (cluster) -> {
            // This test expects to see 2 POST requests to OpenTsdb, but if batch.size > 1 OtsdbBolt will send
            // 1 request with 2 metrics instead of 2 requests with 1 metric.
            // So next property forces OtsdbBolt to send 2 requests.
            Properties properties = getProperties();
            properties.put("opentsdb.batch.size", "1");

            OpenTsdbTopology topology = new OpenTsdbTopology(makeLaunchEnvironment(properties));

            sources.addMockData(ZooKeeperSpout.SPOUT_ID,
                    new Values(LifecycleEvent.builder().signal(Signal.NONE).build(), null));
            sources.addMockData(OpenTsdbTopology.OTSDB_SPOUT_ID,
                    new Values(null, datapoint1), new Values(null, datapoint2));
            completeTopologyParam.setMockedSources(sources);

            StormTopology stormTopology = topology.createTopology();
            stormTopology.get_bolts().remove(ZooKeeperBolt.BOLT_ID);
            activateDatapointParserBolt(stormTopology);

            Testing.completeTopology(cluster, stormTopology, completeTopologyParam);
        });
        //verify that request is sent to OpenTSDB server once
        mockServer.verify(REQUEST, VerificationTimes.exactly(2));
    }

    /**
     * Sets field `active` of DatapointParserBolt to true.
     * TODO Need to be replaced with normal activation by sending START signal or by testing services by unit test
     * At this moment we can't just send START signal to zookeeper spout because order of tuple processing is
     * unpredictable and bolt can handle START signal after handling of test tuple
     */
    private void activateDatapointParserBolt(StormTopology stormTopology) throws IOException, ClassNotFoundException {
        // get bolt instance
        Bolt bolt = stormTopology.get_bolts().get(OTSDB_PARSE_BOLT_ID);
        byte[] serializedBolt = bolt.get_bolt_object().get_serialized_java();
        ObjectInput inputStream = new ObjectInputStream(new ByteArrayInputStream(serializedBolt));
        DatapointParseBolt datapointParseBolt = (DatapointParseBolt) inputStream.readObject();

        // activate bolt
        datapointParseBolt.activate();

        // serialize bolt
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutput outputStream = new ObjectOutputStream(byteArrayOutputStream);
        outputStream.writeObject(datapointParseBolt);
        byte[] updatedBolt = byteArrayOutputStream.toByteArray();
        outputStream.close();
        byteArrayOutputStream.close();

        // replace old bolt with new
        bolt.get_bolt_object().set_serialized_java(updatedBolt);
    }

    private Properties getProperties() {
        Properties properties = new Properties();
        properties.setProperty("opentsdb.hosts", String.format("http://localhost:%d", OPENTSDB_PORT));
        return properties;
    }
}
