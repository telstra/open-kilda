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

package org.openkilda.wfm.topology.isllatency;

import static org.apache.storm.utils.Utils.sleep;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.EmbeddedNeo4jDatabase;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.config.provider.MultiPrefixConfigurationProvider;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

public class IslLatencyTopologyTest extends AbstractStormTest {

    private static final long timestamp = System.currentTimeMillis();
    private static final int POLL_TIMEOUT = 1000;
    private static final String POLL_DATAPOINT_ASSERT_MESSAGE = "Could not poll any datapoint";
    private static final String METRIC_PREFIX = "kilda.";
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int FORWARD_LATENCY = 123;
    private static final int REVERSE_LATENCY = 456;
    private static final long PACKET_ID = 555;
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1L);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2L);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static IslLatencyTopologyConfig islLatencyTopologyConfig;
    private static TestKafkaConsumer otsdbConsumer;
    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static SwitchRepository switchRepository;
    private static IslRepository islRepository;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.startZooKafkaAndStorm();

        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        Properties configOverlay = new Properties();
        configOverlay.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());
        configOverlay.setProperty("opentsdb.metric.prefix", METRIC_PREFIX);
        configOverlay.setProperty("neo4j.indexes.auto", "update"); // ask to create indexes/constraints if needed

        launchEnvironment.setupOverlay(configOverlay);
        MultiPrefixConfigurationProvider configurationProvider = launchEnvironment.getConfigurationProvider();
        PersistenceManager persistenceManager = PersistenceProvider.getInstance()
                .createPersistenceManager(configurationProvider);

        IslLatencyTopology islLatencyTopology = new IslLatencyTopology(launchEnvironment);
        islLatencyTopologyConfig = islLatencyTopology.getConfig();

        StormTopology stormTopology = islLatencyTopology.createTopology();
        Config config = stormConfig();

        cluster.submitTopology(IslLatencyTopologyTest.class.getSimpleName(), config, stormTopology);

        otsdbConsumer = new TestKafkaConsumer(islLatencyTopologyConfig.getKafkaOtsdbTopic(),
                kafkaProperties(UUID.randomUUID().toString()));
        otsdbConsumer.start();

        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        sleep(TOPOLOGY_START_TIMEOUT);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        otsdbConsumer.wakeup();
        otsdbConsumer.join();
        embeddedNeo4jDb.stop();
        AbstractStormTest.stopZooKafkaAndStorm();
    }

    @Before
    public void setup() {
        otsdbConsumer.clear();
        Switch firstSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch secondSwitch = Switch.builder().switchId(SWITCH_ID_2).build();
        switchRepository.createOrUpdate(firstSwitch);
        switchRepository.createOrUpdate(secondSwitch);

        Isl forwardIsl = Isl.builder()
                .srcSwitch(firstSwitch)
                .srcPort(PORT_1)
                .destSwitch(secondSwitch)
                .destPort(PORT_2)
                .actualStatus(IslStatus.ACTIVE)
                .latency(FORWARD_LATENCY)
                .build();
        Isl reverseIsl = Isl.builder()
                .srcSwitch(secondSwitch)
                .srcPort(PORT_2)
                .destSwitch(firstSwitch)
                .destPort(PORT_1)
                .actualStatus(IslStatus.ACTIVE)
                .latency(REVERSE_LATENCY)
                .build();
        islRepository.createOrUpdate(forwardIsl);
        islRepository.createOrUpdate(reverseIsl);
    }

    @After
    public void cleanUp() {
        // force delete will delete all relations (including created Isl)
        switchRepository.forceDelete(SWITCH_ID_1);
        switchRepository.forceDelete(SWITCH_ID_2);
    }

    @Test
    public void roundTripLatencyMetricTest() throws Exception {
        IslRoundTripLatency islRoundTripLatency = new IslRoundTripLatency(
                SWITCH_ID_1, PORT_1, FORWARD_LATENCY, PACKET_ID);
        pushMessageAndAssertMetric(islRoundTripLatency, FORWARD_LATENCY);
    }

    @Test
    public void oneWayLatencyNonNoviflowMetricTest() throws Exception {
        IslOneWayLatency islOneWayLatency = new IslOneWayLatency(
                SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, FORWARD_LATENCY, PACKET_ID, false, false, false);
        pushMessageAndAssertMetric(islOneWayLatency, FORWARD_LATENCY);
    }

    @Test
    public void oneWayLatencyNoviflowMetricTest() throws Exception {
        IslOneWayLatency islOneWayLatency = new IslOneWayLatency(
                SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, FORWARD_LATENCY, PACKET_ID, true, false, true);
        // if dst switch supports round trip latency we must get latency from reverse ISL
        pushMessageAndAssertMetric(islOneWayLatency, REVERSE_LATENCY);
    }

    private void pushMessageAndAssertMetric(InfoData infoData, long expectedLatency) throws JsonProcessingException {
        InfoMessage infoMessage = new InfoMessage(infoData, timestamp, UUID.randomUUID().toString(), null, null);
        String request = objectMapper.writeValueAsString(infoMessage);
        kProducer.pushMessage(islLatencyTopologyConfig.getKafkaTopoIslLatencyTopic(), request);

        Datapoint datapoint = pollDataPoint();

        assertThat(datapoint.getTags().get("src_switch"), is(SWITCH_ID_1.toOtsdFormat()));
        assertThat(datapoint.getTags().get("src_port"), is(String.valueOf(PORT_1)));
        assertThat(datapoint.getTags().get("dst_switch"), is(SWITCH_ID_2.toOtsdFormat()));
        assertThat(datapoint.getTags().get("dst_port"), is(String.valueOf(PORT_2)));
        assertThat(datapoint.getTime(), is(timestamp));
        assertThat(datapoint.getMetric(), is(METRIC_PREFIX + "isl.latency"));
        assertThat(datapoint.getValue().longValue(), is(expectedLatency));
    }

    private Datapoint pollDataPoint() {
        ConsumerRecord<String, String> record = null;
        try {
            record = otsdbConsumer.pollMessage(POLL_TIMEOUT);
            if (record == null) {
                throw new AssertionError(POLL_DATAPOINT_ASSERT_MESSAGE);
            }
            return objectMapper.readValue(record.value(), Datapoint.class);
        } catch (InterruptedException e) {
            throw new AssertionError(POLL_DATAPOINT_ASSERT_MESSAGE);
        } catch (IOException e) {
            throw new AssertionError(String.format("Could not parse datapoint object: '%s'", record.value()));
        }
    }
}
