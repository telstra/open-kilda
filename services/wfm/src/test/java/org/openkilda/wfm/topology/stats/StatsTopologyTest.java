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

package org.openkilda.wfm.topology.stats;

import static java.util.stream.Collectors.toList;
import static org.apache.storm.utils.Utils.sleep;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.openkilda.model.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPair;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.EmbeddedNeo4jDatabase;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.config.provider.MultiPrefixConfigurationProvider;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StatsTopologyTest extends AbstractStormTest {

    private static final long timestamp = System.currentTimeMillis();
    private static final int POLL_TIMEOUT = 1000;
    private static final String POLL_DATAPOINT_ASSERT_MESSAGE = "Could not poll all %d datapoints, got only %d records";
    private static final String METRIC_PREFIX = "kilda.";

    private final SwitchId switchId = new SwitchId(1L);
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private final long cookie = 0x4000000000000001L;
    private final String flowId = "f253423454343";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;

    private static PersistenceManager persistenceManager;
    private static StatsTopologyConfig statsTopologyConfig;

    private static TestKafkaConsumer otsdbConsumer;
    private static FlowPairRepository flowPairRepository;
    private static SwitchRepository switchRepository;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.startZooKafkaAndStorm();

        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        Properties configOverlay = new Properties();
        configOverlay.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());
        configOverlay.setProperty("opentsdb.metric.prefix", METRIC_PREFIX);

        launchEnvironment.setupOverlay(configOverlay);

        MultiPrefixConfigurationProvider configurationProvider = launchEnvironment.getConfigurationProvider();
        persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);

        StatsTopology statsTopology = new StatsTopology(launchEnvironment);
        statsTopologyConfig = statsTopology.getConfig();

        StormTopology stormTopology = statsTopology.createTopology();
        Config config = stormConfig();

        cluster.submitTopology(StatsTopologyTest.class.getSimpleName(), config, stormTopology);

        otsdbConsumer = new TestKafkaConsumer(statsTopologyConfig.getKafkaOtsdbTopic(),
                kafkaProperties(UUID.randomUUID().toString()));
        otsdbConsumer.start();

        flowPairRepository = persistenceManager.getRepositoryFactory().createFlowPairRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

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
    public void setup() throws IOException {
        otsdbConsumer.clear();

        // need clear data in CacheBolt
        for (FlowPair flowPair : flowPairRepository.findAll()) {
            sendRemoveFlowCommand(flowPair.getForward());
            flowPairRepository.delete(flowPair);
        }

        for (Switch sw : switchRepository.findAll()) {
            switchRepository.delete(sw);
        }
        ((Neo4jSessionFactory) persistenceManager.getTransactionManager()).getSession().purgeDatabase();
    }

    @Test
    public void portStatsTest() throws Exception {
        final SwitchId switchId = new SwitchId(1L);
        int portCount = 52;

        final List<PortStatsEntry> entries = IntStream.range(1, portCount + 1).boxed().map(port -> {
            int baseCount = port * 20;
            return new PortStatsEntry(port, baseCount, baseCount + 1, baseCount + 2, baseCount + 3,
                    baseCount + 4, baseCount + 5, baseCount + 6, baseCount + 7,
                    baseCount + 8, baseCount + 9, baseCount + 10, baseCount + 11);
        }).collect(toList());

        sendStatsMessage(new PortStatsData(switchId, entries));

        List<Datapoint> datapoints = pollDatapoints(728);

        Map<String, Map<String, Number>> metricsByPort = new HashMap<>();
        for (int i = 1; i <= portCount; i++) {
            metricsByPort.put(String.valueOf(i), new HashMap<>());
        }

        datapoints.forEach(datapoint -> {
            assertThat(datapoint.getTags().get("switchid"), is(switchId.toOtsdFormat()));
            assertThat(datapoint.getTime(), is(timestamp));
            assertThat(datapoint.getMetric(), startsWith(METRIC_PREFIX + "switch"));

            metricsByPort.get(datapoint.getTags().get("port"))
                    .put(datapoint.getMetric(), datapoint.getValue());
        });

        for (int i = 1; i <= portCount; i++) {
            Map<String, Number> metrics = metricsByPort.get(String.valueOf(i));
            assertEquals(14, metrics.size());

            int baseCount = i * 20;
            assertEquals(baseCount, metrics.get(METRIC_PREFIX + "switch.rx-packets"));
            assertEquals(baseCount + 1, metrics.get(METRIC_PREFIX + "switch.tx-packets"));
            assertEquals(baseCount + 2, metrics.get(METRIC_PREFIX + "switch.rx-bytes"));
            assertEquals((baseCount + 2) * 8, metrics.get(METRIC_PREFIX + "switch.rx-bits"));
            assertEquals(baseCount + 3, metrics.get(METRIC_PREFIX + "switch.tx-bytes"));
            assertEquals((baseCount + 3) * 8, metrics.get(METRIC_PREFIX + "switch.tx-bits"));
            assertEquals(baseCount + 4, metrics.get(METRIC_PREFIX + "switch.rx-dropped"));
            assertEquals(baseCount + 5, metrics.get(METRIC_PREFIX + "switch.tx-dropped"));
            assertEquals(baseCount + 6, metrics.get(METRIC_PREFIX + "switch.rx-errors"));
            assertEquals(baseCount + 7, metrics.get(METRIC_PREFIX + "switch.tx-errors"));
            assertEquals(baseCount + 8, metrics.get(METRIC_PREFIX + "switch.rx-frame-error"));
            assertEquals(baseCount + 9, metrics.get(METRIC_PREFIX + "switch.rx-over-error"));
            assertEquals(baseCount + 10, metrics.get(METRIC_PREFIX + "switch.rx-crc-error"));
            assertEquals(baseCount + 11, metrics.get(METRIC_PREFIX + "switch.collisions"));
        }
    }

    @Test
    public void meterConfigStatsTest() throws Exception {
        final SwitchId switchId = new SwitchId(1L);
        final List<MeterConfigReply> stats =
                Collections.singletonList(new MeterConfigReply(2, Arrays.asList(1L, 2L, 3L)));
        sendStatsMessage(new MeterConfigStatsData(switchId, stats));

        List<Datapoint> datapoints = pollDatapoints(3);

        datapoints.forEach(datapoint -> {
            assertThat(datapoint.getTags().get("switchid"), is(switchId.toOtsdFormat()));
            assertThat(datapoint.getTime(), is(timestamp));
            assertThat(datapoint.getMetric(), is(METRIC_PREFIX + "switch.meters"));
        });
    }

    @Test
    public void meterSystemRulesStatsTest() throws IOException {
        long meterId = MeterId.createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE).getValue();
        MeterStatsEntry meterStats = new MeterStatsEntry(meterId, 400L, 500L);

        sendStatsMessage(new MeterStatsData(switchId, Collections.singletonList(meterStats)));

        List<Datapoint> datapoints = pollDatapoints(3);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(meterStats.getPacketsInCount(),
                datapointMap.get(METRIC_PREFIX + "switch.flow.system.meter.packets").getValue().longValue());
        assertEquals(meterStats.getByteInCount(),
                datapointMap.get(METRIC_PREFIX + "switch.flow.system.meter.bytes").getValue().longValue());
        assertEquals(meterStats.getByteInCount() * 8,
                datapointMap.get(METRIC_PREFIX + "switch.flow.system.meter.bits").getValue().longValue());

        datapoints.forEach(datapoint -> {
            assertEquals(3, datapoint.getTags().size());
            assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(String.valueOf(meterId), datapoint.getTags().get("meterid"));
            assertEquals(Cookie.toString(VERIFICATION_BROADCAST_RULE_COOKIE), datapoint.getTags().get("cookieHex"));
            assertEquals(timestamp, datapoint.getTime().longValue());
        });
    }

    @Test
    public void meterFlowRulesStatsTest() throws IOException {
        FlowPair flowPair = createFlow(switchId, flowId);
        sendInstallOneSwitchFlowCommand(flowPair.getForward());

        MeterStatsEntry meterStats = new MeterStatsEntry(flowPair.getForward().getMeterId(), 500L, 700L);

        sendStatsMessage(new MeterStatsData(switchId, Collections.singletonList(meterStats)));

        List<Datapoint> datapoints = pollDatapoints(3);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(meterStats.getPacketsInCount(),
                datapointMap.get(METRIC_PREFIX + "flow.meter.packets").getValue().longValue());
        assertEquals(meterStats.getByteInCount(),
                datapointMap.get(METRIC_PREFIX + "flow.meter.bytes").getValue().longValue());
        assertEquals(meterStats.getByteInCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.meter.bits").getValue().longValue());


        datapoints.forEach(datapoint -> {
            assertEquals(5, datapoint.getTags().size());
            assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(String.valueOf(flowPair.getForward().getMeterId()), datapoint.getTags().get("meterid"));
            assertEquals("forward", datapoint.getTags().get("direction"));
            assertEquals(flowId, datapoint.getTags().get("flowid"));
            assertEquals(String.valueOf(flowPair.getForward().getCookie()), datapoint.getTags().get("cookie"));
            assertEquals(timestamp, datapoint.getTime().longValue());
        });
    }

    @Test
    public void flowStatsTest() throws Exception {
        FlowPair flowPair = createFlow(switchId, flowId);
        sendInstallOneSwitchFlowCommand(flowPair.getForward());

        FlowStatsEntry flowStats = new FlowStatsEntry((short) 1, cookie, 150L, 300L);

        sendStatsMessage(new FlowStatsData(switchId, Collections.singletonList(flowStats)));

        List<Datapoint> datapoints = pollDatapoints(9);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(flowStats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "flow.raw.packets").getValue().longValue());
        assertEquals(flowStats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "flow.ingress.packets").getValue().longValue());
        assertEquals(flowStats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "flow.packets").getValue().longValue());

        assertEquals(flowStats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "flow.raw.bytes").getValue().longValue());
        assertEquals(flowStats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "flow.ingress.bytes").getValue().longValue());
        assertEquals(flowStats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "flow.bytes").getValue().longValue());

        assertEquals(flowStats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.raw.bits").getValue().longValue());
        assertEquals(flowStats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.ingress.bits").getValue().longValue());
        assertEquals(flowStats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.bits").getValue().longValue());

        datapoints.forEach(datapoint -> {
            switch (datapoint.getMetric()) {
                case METRIC_PREFIX + "flow.raw.packets":
                case METRIC_PREFIX + "flow.raw.bytes":
                case METRIC_PREFIX + "flow.raw.bits":
                    assertEquals(5, datapoint.getTags().size());
                    assertEquals(flowId, datapoint.getTags().get("flowid"));
                    assertEquals("forward", datapoint.getTags().get("direction"));
                    assertEquals(String.valueOf(flowStats.getTableId()), datapoint.getTags().get("tableid"));
                    assertEquals(String.valueOf(cookie), datapoint.getTags().get("cookie"));
                    assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
                    break;
                case METRIC_PREFIX + "flow.ingress.packets":
                case METRIC_PREFIX + "flow.ingress.bytes":
                case METRIC_PREFIX + "flow.ingress.bits":
                case METRIC_PREFIX + "flow.packets":
                case METRIC_PREFIX + "flow.bytes":
                case METRIC_PREFIX + "flow.bits":
                    assertEquals(2, datapoint.getTags().size());
                    assertEquals(flowId, datapoint.getTags().get("flowid"));
                    assertEquals("forward", datapoint.getTags().get("direction"));
                    break;
                default:
                    throw new AssertionError(String.format("Unknown metric: %s", datapoint.getMetric()));
            }
        });
    }

    @Test
    public void systemRulesStatsTest() throws Exception {
        FlowStatsEntry systemRuleStats = new FlowStatsEntry((short) 1, VERIFICATION_BROADCAST_RULE_COOKIE, 100L, 200L);

        sendStatsMessage(new FlowStatsData(switchId, Collections.singletonList(systemRuleStats)));

        List<Datapoint> datapoints = pollDatapoints(3);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(systemRuleStats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "switch.flow.system.packets").getValue().longValue());
        assertEquals(systemRuleStats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "switch.flow.system.bytes").getValue().longValue());
        assertEquals(systemRuleStats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "switch.flow.system.bits").getValue().longValue());


        datapoints.forEach(datapoint -> {
            assertEquals(2, datapoint.getTags().size());
            assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(Cookie.toString(VERIFICATION_BROADCAST_RULE_COOKIE), datapoint.getTags().get("cookieHex"));
        });
    }

    private FlowPair createFlow(SwitchId switchId, String flowId) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();

        Switch sw = Switch.builder().switchId(switchId).build();
        switchRepository.createOrUpdate(sw);

        FlowPairRepository flowPairRepository = repositoryFactory.createFlowPairRepository();
        FlowPair flowPair = new FlowPair(flowId, sw, 1, 5, sw, 2, 5);
        flowPair.getForward().setCookie(cookie);
        flowPair.getForward().setMeterId(456L);
        flowPairRepository.createOrUpdate(flowPair);
        return flowPair;
    }

    private void sendStatsMessage(InfoData infoData) throws IOException {
        InfoMessage infoMessage = new InfoMessage(infoData, timestamp, UUID.randomUUID().toString(),
                Destination.WFM_STATS);
        sendMessage(infoMessage, statsTopologyConfig.getKafkaStatsTopic());
    }

    private void sendRemoveFlowCommand(UnidirectionalFlow flow) throws IOException {
        RemoveFlow removeFlow = new RemoveFlow(TRANSACTION_ID, flow.getFlowId(), flow.getCookie(),
                flow.getSrcSwitch().getSwitchId(), flow.getMeterId(), null);
        sendFlowCommand(removeFlow);
    }

    private void sendInstallOneSwitchFlowCommand(UnidirectionalFlow flow) throws IOException {
        InstallOneSwitchFlow installOneSwitchFlow = new InstallOneSwitchFlow(
                TRANSACTION_ID,
                flow.getFlowId(),
                flow.getCookie(),
                flow.getSrcSwitch().getSwitchId(),
                flow.getSrcPort(),
                flow.getDestPort(),
                flow.getSrcVlan(),
                flow.getDestVlan(),
                OutputVlanType.PUSH,
                flow.getBandwidth(),
                flow.getMeterId().longValue());
        sendFlowCommand(installOneSwitchFlow);
    }

    private void sendFlowCommand(BaseFlow flowCommand) throws IOException {
        CommandMessage commandMessage = new CommandMessage(flowCommand, timestamp, UUID.randomUUID().toString());
        sendMessage(commandMessage, statsTopologyConfig.getKafkaSpeakerFlowTopic());
    }

    private void sendMessage(Object object, String topic) throws IOException {
        String request = objectMapper.writeValueAsString(object);
        kProducer.pushMessage(topic, request);
    }

    private List<Datapoint> pollDatapoints(int expectedDatapointCount) {
        List<Datapoint> datapoints = new ArrayList<>();

        for (int i = 0; i < expectedDatapointCount; i++) {
            ConsumerRecord<String, String> record = null;
            try {
                record = otsdbConsumer.pollMessage(POLL_TIMEOUT);
                if (record == null) {
                    throw new AssertionError(String.format(POLL_DATAPOINT_ASSERT_MESSAGE,
                            expectedDatapointCount, datapoints.size()));
                }
                Datapoint datapoint = objectMapper.readValue(record.value(), Datapoint.class);
                datapoints.add(datapoint);
            } catch (InterruptedException e) {
                throw new AssertionError(String.format(POLL_DATAPOINT_ASSERT_MESSAGE,
                        expectedDatapointCount, datapoints.size()));
            } catch (IOException e) {
                throw new AssertionError(String.format("Could not parse datapoint object: '%s'", record.value()));
            }
        }
        return datapoints;
    }

    private Map<String, Datapoint> createDatapointMap(List<Datapoint> datapoints) {
        return datapoints
                .stream()
                .collect(Collectors.toMap(Datapoint::getMetric, Function.identity()));
    }
}
