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
import static org.junit.Assert.assertEquals;

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
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.topology.TestKafkaConsumer;
import org.openkilda.wfm.topology.isllatency.service.IslLatencyService;

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

    private static final Long timestamp = System.currentTimeMillis();
    private static final int POLL_TIMEOUT = 1000;
    private static final String POLL_DATAPOINT_ASSERT_MESSAGE = "Could not poll any datapoint";
    private static final String METRIC_PREFIX = "kilda.";
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int INITIAL_FORWARD_LATENCY = 123;
    private static final int INITIAL_REVERSE_LATENCY = 456;
    private static final int NEW_FORWARD_LATENCY = 789;
    private static final long PACKET_ID = 555;
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1L);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2L);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String NOVIFLOW_MANUFACTURER = "Noviflow";
    private static IslLatencyTopologyConfig islLatencyTopologyConfig;
    private static TestKafkaConsumer otsdbConsumer;
    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static SwitchRepository switchRepository;
    private static IslRepository islRepository;
    private static IslLatencyService islLatencyService;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.startZooKafkaAndStorm();

        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        Properties configOverlay = new Properties();
        configOverlay.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());
        configOverlay.setProperty("opentsdb.metric.prefix", METRIC_PREFIX);
        configOverlay.setProperty("neo4j.indexes.auto", "update");

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
        islLatencyService = new IslLatencyService(
                persistenceManager.getTransactionManager(), persistenceManager.getRepositoryFactory());

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
        Switch firstSwitch = createSwitch(SWITCH_ID_1);
        Switch secondSwitch = createSwitch(SWITCH_ID_2);
        createIsl(firstSwitch, PORT_1, secondSwitch, PORT_2, INITIAL_FORWARD_LATENCY);
        createIsl(secondSwitch, PORT_2, firstSwitch, PORT_1, INITIAL_REVERSE_LATENCY);
    }

    @After
    public void cleanUp() {
        // force delete will delete all relations (including created Isl)
        switchRepository.forceDelete(SWITCH_ID_1);
        switchRepository.forceDelete(SWITCH_ID_2);
    }

    @Test
    public void roundTripLatencyTest() throws IslNotFoundException, IllegalIslStateException, JsonProcessingException {
        assertEquals(INITIAL_FORWARD_LATENCY, islLatencyService.getIsl(SWITCH_ID_1, PORT_1).getLatency());

        IslRoundTripLatency islRoundTripLatency = new IslRoundTripLatency(
                SWITCH_ID_1, PORT_1, NEW_FORWARD_LATENCY, PACKET_ID);
        pushMessageAndAssertMetric(islRoundTripLatency, NEW_FORWARD_LATENCY); // check OpenTSDB
        assertEquals(NEW_FORWARD_LATENCY, islLatencyService.getIsl(SWITCH_ID_1, PORT_1).getLatency()); // check Neo4j DB
    }

    @Test
    public void oneWayLatencyNonNoviflowTest() throws IslNotFoundException, JsonProcessingException {
        assertEquals(INITIAL_FORWARD_LATENCY,
                islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency());

        IslOneWayLatency islOneWayLatency = new IslOneWayLatency(
                SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, NEW_FORWARD_LATENCY, PACKET_ID, false, false, false);
        pushMessageAndAssertMetric(islOneWayLatency, NEW_FORWARD_LATENCY); // check OpenTSDB
        assertEquals(NEW_FORWARD_LATENCY,
                islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency()); // check Neo4j DB
    }

    @Test
    public void oneWayLatencyCopyFromReverseTest() throws IslNotFoundException, JsonProcessingException {
        assertEquals(INITIAL_FORWARD_LATENCY,
                islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency());

        IslOneWayLatency islOneWayLatency = new IslOneWayLatency(
                SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, NEW_FORWARD_LATENCY, PACKET_ID, false, true, true);
        // if src switch supports copy field action and dst switch supports groups we must use latency from reverse ISL
        pushMessageAndAssertMetric(islOneWayLatency, INITIAL_REVERSE_LATENCY); // check OpenTSDB
        assertEquals(INITIAL_REVERSE_LATENCY,
                islLatencyService.getIsl(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2).getLatency()); // check Neo4j DB
    }

    private void pushMessageAndAssertMetric(InfoData infoData, long expectedLatency) throws JsonProcessingException {
        InfoMessage infoMessage = new InfoMessage(infoData, timestamp, UUID.randomUUID().toString(), null, null);
        String request = objectMapper.writeValueAsString(infoMessage);
        kProducer.pushMessage(islLatencyTopologyConfig.getKafkaTopoIslLatencyTopic(), request);

        Datapoint datapoint = pollDataPoint();

        assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("src_switch"));
        assertEquals(String.valueOf(PORT_1), datapoint.getTags().get("src_port"));
        assertEquals(SWITCH_ID_2.toOtsdFormat(), datapoint.getTags().get("dst_switch"));
        assertEquals(String.valueOf(PORT_2), datapoint.getTags().get("dst_port"));
        assertEquals(timestamp, datapoint.getTime());
        assertEquals(METRIC_PREFIX + "isl.latency", datapoint.getMetric());
        assertEquals(expectedLatency, datapoint.getValue().longValue());
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

    private static Switch createSwitch(SwitchId switchId) {
        Switch sw = new Switch();
        sw.setSwitchId(switchId);
        sw.setOfDescriptionManufacturer(NOVIFLOW_MANUFACTURER);
        switchRepository.createOrUpdate(sw);
        return sw;
    }

    private void createIsl(Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort, int latency) {
        Isl isl = Isl.builder()
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .actualStatus(IslStatus.ACTIVE)
                .latency(latency).build();
        islRepository.createOrUpdate(isl);
    }
}
