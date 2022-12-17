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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.storm.utils.Utils.sleep;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.openkilda.model.FlowPathDirection.FORWARD;
import static org.openkilda.model.FlowPathDirection.REVERSE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.wfm.config.KafkaConfig.STATS_TOPOLOGY_TEST_KAFKA_PORT;
import static org.openkilda.wfm.config.ZookeeperConfig.STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.grpc.GetPacketInOutStatsResponse;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveYFlowStatsInfo;
import org.openkilda.messaging.info.stats.StatsNotification;
import org.openkilda.messaging.info.stats.SwitchTableStatsData;
import org.openkilda.messaging.info.stats.TableStatsEntry;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateYFlowStatsInfo;
import org.openkilda.messaging.model.grpc.PacketInOutStatsDto;
import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StatsTopologyTest extends AbstractStormTest {

    private static final long timestamp = System.currentTimeMillis();
    private static final int POLL_TIMEOUT = 1000;
    private static final String POLL_DATAPOINT_ASSERT_MESSAGE = "Could not poll all %d datapoints, got only %d records";
    private static final String METRIC_PREFIX = "kilda.";
    private static final int ENCAPSULATION_ID = 123;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String COMPONENT_NAME = "stats";
    public static final String RUN_ID = "blue";
    public static final String ROOT_NODE = "kilda";
    private static final int STAT_VLAN_1 = 4;
    private static final int STAT_VLAN_2 = 5;
    public static final HashSet<Integer> STAT_VLANS = Sets.newHashSet(STAT_VLAN_1, STAT_VLAN_2);
    private static InMemoryGraphPersistenceManager persistenceManager;
    private static StatsTopologyConfig statsTopologyConfig;
    private static TestKafkaConsumer otsdbConsumer;
    private static FlowRepository flowRepository;
    private static YFlowRepository yFlowRepository;
    private static SwitchRepository switchRepository;
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1L);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2L);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3L);
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int PORT_3 = 3;
    private static final int PORT_4 = 4;
    private static final MeterId SHARED_POINT_METER_ID = new MeterId(4);
    private static final MeterId Y_POINT_METER_ID = new MeterId(5);
    private static final long MAIN_COOKIE = 15;
    private static final long PROTECTED_COOKIE = 17;
    private static final FlowSegmentCookie MAIN_FORWARD_COOKIE = new FlowSegmentCookie(FORWARD, MAIN_COOKIE);
    private static final FlowSegmentCookie MAIN_REVERSE_COOKIE = new FlowSegmentCookie(REVERSE, MAIN_COOKIE);
    private static final FlowSegmentCookie PROTECTED_FORWARD_COOKIE = new FlowSegmentCookie(FORWARD, PROTECTED_COOKIE);
    private static final FlowSegmentCookie PROTECTED_REVERSE_COOKIE = new FlowSegmentCookie(REVERSE, PROTECTED_COOKIE);
    private static final FlowSegmentCookie FORWARD_MIRROR_COOKIE = MAIN_FORWARD_COOKIE.toBuilder().mirror(true).build();
    private static final FlowSegmentCookie REVERSE_MIRROR_COOKIE = MAIN_REVERSE_COOKIE.toBuilder().mirror(true).build();
    private static final FlowSegmentCookie Y_MAIN_FORWARD_COOKIE = MAIN_FORWARD_COOKIE.toBuilder().yFlow(true).build();
    private static final FlowSegmentCookie Y_MAIN_REVERSE_COOKIE = MAIN_REVERSE_COOKIE.toBuilder().yFlow(true).build();
    private static final FlowSegmentCookie STAT_VLAN_FORWARD_COOKIE_1 = MAIN_FORWARD_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_1).build();
    private static final FlowSegmentCookie STAT_VLAN_FORWARD_COOKIE_2 = MAIN_FORWARD_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_2).build();
    private static final FlowSegmentCookie STAT_VLAN_REVERSE_COOKIE_1 = MAIN_REVERSE_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_1).build();
    private static final FlowSegmentCookie STAT_VLAN_REVERSE_COOKIE_2 = MAIN_REVERSE_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_2).build();
    private static final String flowId = "f253423454343";
    private static final String Y_FLOW_ID = "Y_flow_1";

    @BeforeClass
    public static void setupOnce() throws Exception {
        Properties configOverlay = getZooKeeperProperties(STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT, ROOT_NODE);
        configOverlay.putAll(getKafkaProperties(STATS_TOPOLOGY_TEST_KAFKA_PORT));
        configOverlay.setProperty("opentsdb.metric.prefix", METRIC_PREFIX);

        AbstractStormTest.startZooKafka(configOverlay);
        setStartSignal(STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT, ROOT_NODE, COMPONENT_NAME, RUN_ID);
        AbstractStormTest.startStorm(COMPONENT_NAME, RUN_ID);

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        launchEnvironment.setupOverlay(configOverlay);

        persistenceManager = InMemoryGraphPersistenceManager.newInstance();
        persistenceManager.install();

        StatsTopology statsTopology = new StatsTopology(launchEnvironment, persistenceManager);
        statsTopologyConfig = statsTopology.getConfig();

        StormTopology stormTopology = statsTopology.createTopology();
        Config config = stormConfig();

        cluster.submitTopology(StatsTopologyTest.class.getSimpleName(), config, stormTopology);

        otsdbConsumer = new TestKafkaConsumer(statsTopologyConfig.getKafkaOtsdbTopic(),
                kafkaConsumerProperties(UUID.randomUUID().toString(), COMPONENT_NAME, RUN_ID));
        otsdbConsumer.start();

        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        sleep(TOPOLOGY_START_TIMEOUT);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        otsdbConsumer.wakeup();
        otsdbConsumer.join();

        AbstractStormTest.stopZooKafkaAndStorm();
    }

    @Before
    public void setup() {
        otsdbConsumer.clear();

        // need clear data in CacheBolt
        for (Flow flow : flowRepository.findAll()) {
            flow.getPaths().forEach(path -> sendRemoveFlowPathInfo(path, flow.getVlanStatistics()));
        }
        for (YFlow yFlow : yFlowRepository.findAll()) {
            sendRemoveYFlowPathInfo(yFlow);
        }

        persistenceManager.getInMemoryImplementation().purgeData();
    }

    @Test
    public void portStatsTest() {
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
    public void meterConfigStatsTest() {
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
    public void meterSystemRulesStatsTest() {
        long meterId = MeterId.createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE).getValue();
        MeterStatsEntry meterStats = new MeterStatsEntry(meterId, 400L, 500L);

        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(meterStats)));

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
            assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(String.valueOf(meterId), datapoint.getTags().get("meterid"));
            assertEquals(Cookie.toString(VERIFICATION_BROADCAST_RULE_COOKIE), datapoint.getTags().get("cookieHex"));
            assertEquals(timestamp, datapoint.getTime().longValue());
        });
    }

    @Test
    public void packetInOutStatsTest() {
        PacketInOutStatsDto data = new PacketInOutStatsDto();
        data.setPacketInTotalPackets(1);
        data.setPacketInTotalPacketsDataplane(2);
        data.setPacketInNoMatchPackets(3);
        data.setPacketInApplyActionPackets(4);
        data.setPacketInInvalidTtlPackets(5);
        data.setPacketInActionSetPackets(6);
        data.setPacketInGroupPackets(7);
        data.setPacketInPacketOutPackets(8);
        data.setPacketOutTotalPacketsDataplane(9);
        data.setPacketOutTotalPacketsHost(10);
        data.setPacketOutEth0InterfaceUp(true);

        sendStatsMessage(new GetPacketInOutStatsResponse(SWITCH_ID_1, data));

        List<Datapoint> datapoints = pollDatapoints(11);

        Map<String, Datapoint> map = createDatapointMap(datapoints);

        assertMetric(data.getPacketInTotalPackets(), "switch.packet-in.total-packets", map);
        assertMetric(data.getPacketInTotalPacketsDataplane(), "switch.packet-in.total-packets.dataplane", map);
        assertMetric(data.getPacketInNoMatchPackets(), "switch.packet-in.no-match.packets", map);
        assertMetric(data.getPacketInApplyActionPackets(), "switch.packet-in.apply-action.packets", map);
        assertMetric(data.getPacketInInvalidTtlPackets(), "switch.packet-in.invalid-ttl.packets", map);
        assertMetric(data.getPacketInActionSetPackets(), "switch.packet-in.action-set.packets", map);
        assertMetric(data.getPacketInGroupPackets(), "switch.packet-in.group.packets", map);
        assertMetric(data.getPacketInPacketOutPackets(), "switch.packet-in.packet-out.packets", map);
        assertMetric(data.getPacketOutTotalPacketsHost(), "switch.packet-out.total-packets.host", map);
        assertMetric(data.getPacketOutTotalPacketsDataplane(), "switch.packet-out.total-packets.dataplane", map);
        assertMetric(1, "switch.packet-out.eth0-interface-up", map);

        datapoints.forEach(datapoint -> {
            assertEquals(1, datapoint.getTags().size());
            assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(timestamp, datapoint.getTime().longValue());
        });
    }

    @Test
    public void meterFlowRulesStatsTest() {
        Flow flow = createOneSwitchFlow(SWITCH_ID_1);
        FlowPath flowPath = flow.getForwardPath();
        sendUpdateFlowPathInfo(flowPath, flow.getVlanStatistics());

        MeterStatsEntry meterStats = new MeterStatsEntry(flowPath.getMeterId().getValue(), 500L, 700L);

        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(meterStats)));

        List<Datapoint> datapoints = pollDatapoints(3);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(meterStats.getPacketsInCount(),
                datapointMap.get(METRIC_PREFIX + "flow.meter.packets").getValue().longValue());
        assertEquals(meterStats.getByteInCount(),
                datapointMap.get(METRIC_PREFIX + "flow.meter.bytes").getValue().longValue());
        assertEquals(meterStats.getByteInCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.meter.bits").getValue().longValue());


        datapoints.forEach(datapoint -> {
            assertEquals(6, datapoint.getTags().size());
            assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(String.valueOf(flowPath.getMeterId().getValue()), datapoint.getTags().get("meterid"));
            assertEquals("forward", datapoint.getTags().get("direction"));
            assertEquals("false", datapoint.getTags().get("is_y_flow_subflow"));
            assertEquals(flowId, datapoint.getTags().get("flowid"));
            assertEquals(String.valueOf(flowPath.getCookie().getValue()), datapoint.getTags().get("cookie"));
            assertEquals(timestamp, datapoint.getTime().longValue());
        });
    }

    @Test
    public void lldpMeterStatsTest() {
        long lldpMeterId = MeterId.MIN_FLOW_METER_ID;

        MeterStatsEntry meterStats = new MeterStatsEntry(lldpMeterId, 400L, 500L);

        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(meterStats)));

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
            assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(String.valueOf(lldpMeterId), datapoint.getTags().get("meterid"));
            assertEquals("unknown", datapoint.getTags().get("direction"));
            assertEquals("unknown", datapoint.getTags().get("cookie"));
            assertEquals("unknown", datapoint.getTags().get("flowid"));
            assertEquals(timestamp, datapoint.getTime().longValue());
        });
    }

    @Test
    public void flowStatsTest() {
        Flow flow = createOneSwitchFlow(SWITCH_ID_1);
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());

        FlowStatsEntry flowStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 150L, 300L, 10, 10);

        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowStats)));
        validateFlowStats(flowStats, MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, true);
    }

    @Test
    public void flowStatsWithProtectedTest() {
        Flow flow = createFlowWithProtectedPath();
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getReversePath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getProtectedForwardPath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getProtectedReversePath(), flow.getVlanStatistics());

        FlowStatsEntry mainForwardStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(mainForwardStats)));
        validateFlowStats(mainForwardStats, MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, false);

        FlowStatsEntry mainReverseStats = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(mainReverseStats)));
        validateFlowStats(mainReverseStats, MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, true);

        FlowStatsEntry protectedStats = new FlowStatsEntry(1, PROTECTED_REVERSE_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(protectedStats)));
        validateFlowStats(protectedStats, PROTECTED_REVERSE_COOKIE, SWITCH_ID_1, false, true);

        FlowStatsEntry transitForwardStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 13, 14, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(transitForwardStats)));
        validateFlowStats(transitForwardStats, MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false);

        FlowStatsEntry transitReverseStats = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 17, 18, 19, 20);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(transitReverseStats)));
        validateFlowStats(transitReverseStats, MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false);
    }

    @Test
    public void ySubFlowIngressStatsTest() {
        Flow flow = createYSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardIngress = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowForwardIngress)));
        validateFlowStats(flowForwardIngress, MAIN_FORWARD_COOKIE, SWITCH_ID_1, false, false, true);

        FlowStatsEntry yForwardIngress = new FlowStatsEntry(1, Y_MAIN_FORWARD_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(yForwardIngress)));
        validateFlowStats(yForwardIngress, Y_MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, false, true);

        FlowStatsEntry flowReverseIngress = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(flowReverseIngress)));
        validateFlowStats(flowReverseIngress, MAIN_REVERSE_COOKIE, SWITCH_ID_3, true, false, true);
    }

    @Test
    public void ySubFlowEgressStatsTest() {
        Flow flow = createYSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowReverseEgress = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowReverseEgress)));
        validateFlowStats(flowReverseEgress, MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, true, true);

        FlowStatsEntry flowForwardEgress = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(flowForwardEgress)));
        validateFlowStats(flowForwardEgress, MAIN_FORWARD_COOKIE, SWITCH_ID_3, false, true, true);
    }

    @Test
    public void ySubFlowTransitStatsTest() {
        Flow flow = createYSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardTransit = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(flowForwardTransit)));
        validateFlowStats(flowForwardTransit, MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry flowReverseTransit = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(flowReverseTransit)));
        validateFlowStats(flowReverseTransit, MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry yForwardTransit = new FlowStatsEntry(1, Y_MAIN_FORWARD_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(yForwardTransit)));
        validateFlowStats(yForwardTransit, Y_MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry yReverseTransit = new FlowStatsEntry(1, Y_MAIN_REVERSE_COOKIE.getValue(), 13, 14, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(yReverseTransit)));
        validateFlowStats(yReverseTransit, Y_MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false, true);
    }

    @Test
    public void vkindSubFlowIngressStatsTest() {
        Flow flow = createVSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardIngress = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowForwardIngress)));
        validateFlowStats(flowForwardIngress, MAIN_FORWARD_COOKIE, SWITCH_ID_1, false, false, true);

        FlowStatsEntry yForwardIngress = new FlowStatsEntry(1, Y_MAIN_FORWARD_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(yForwardIngress)));
        validateFlowStats(yForwardIngress, Y_MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, false, true);

        FlowStatsEntry flowReverseIngress = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(flowReverseIngress)));
        validateFlowStats(flowReverseIngress, MAIN_REVERSE_COOKIE, SWITCH_ID_3, true, false, true);
    }

    @Test
    public void vkindSubFlowEgressStatsTest() {
        Flow flow = createVSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardEgress = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(flowForwardEgress)));
        validateFlowStats(flowForwardEgress, MAIN_FORWARD_COOKIE, SWITCH_ID_3, false, true, true);

        FlowStatsEntry flowReverseEgress = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowReverseEgress)));
        validateFlowStats(flowReverseEgress, MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, false, true);

        FlowStatsEntry yReverseEgress = new FlowStatsEntry(1, Y_MAIN_REVERSE_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(yReverseEgress)));
        validateFlowStats(yReverseEgress, Y_MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, true, true);
    }

    @Test
    public void vkindSubFlowTransitStatsTest() {
        Flow flow = createYSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardTransit = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(flowForwardTransit)));
        validateFlowStats(flowForwardTransit, MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry flowReverseTransit = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(flowReverseTransit)));
        validateFlowStats(flowReverseTransit, MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry yForwardTransit = new FlowStatsEntry(1, Y_MAIN_FORWARD_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(yForwardTransit)));
        validateFlowStats(yForwardTransit, Y_MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry yReverseTransit = new FlowStatsEntry(1, Y_MAIN_REVERSE_COOKIE.getValue(), 13, 14, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(yReverseTransit)));
        validateFlowStats(yReverseTransit, Y_MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false, true);
    }

    @Test
    public void flatSubFlowIngressStatsTest() {
        Flow flow = createFlatSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardIngress = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowForwardIngress)));
        validateFlowStats(flowForwardIngress, MAIN_FORWARD_COOKIE, SWITCH_ID_1, false, false, true);

        FlowStatsEntry yForwardIngress = new FlowStatsEntry(1, Y_MAIN_FORWARD_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(yForwardIngress)));
        validateFlowStats(yForwardIngress, Y_MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, false, true);

        FlowStatsEntry flowReverseIngress = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(flowReverseIngress)));
        validateFlowStats(flowReverseIngress, MAIN_REVERSE_COOKIE, SWITCH_ID_3, false, false, true);

        FlowStatsEntry yReverseIngress = new FlowStatsEntry(1, Y_MAIN_REVERSE_COOKIE.getValue(), 13, 14, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(yReverseIngress)));
        validateFlowStats(yReverseIngress, Y_MAIN_REVERSE_COOKIE, SWITCH_ID_3, true, false, true);
    }

    @Test
    public void flatSubFlowEgressStatsTest() {
        Flow flow = createFlatSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardEgress = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(flowForwardEgress)));
        validateFlowStats(flowForwardEgress, MAIN_FORWARD_COOKIE, SWITCH_ID_3, false, true, true);

        FlowStatsEntry flowReverseEgress = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowReverseEgress)));
        validateFlowStats(flowReverseEgress, MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, true, true);
    }

    @Test
    public void flatSubFlowTransitStatsTest() {
        Flow flow = createFlatSubFlow();
        sendUpdateSubFlowPathInfo(flow.getForwardPath(), flow);
        sendUpdateSubFlowPathInfo(flow.getReversePath(), flow);
        sendUpdateYFlowPathInfo(flow.getYFlow());

        FlowStatsEntry flowForwardTransit = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(flowForwardTransit)));
        validateFlowStats(flowForwardTransit, MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry flowReverseTransit = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(flowReverseTransit)));
        validateFlowStats(flowReverseTransit, MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry yForwardTransit = new FlowStatsEntry(1, Y_MAIN_FORWARD_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(yForwardTransit)));
        validateFlowStats(yForwardTransit, Y_MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false, true);

        FlowStatsEntry yReverseTransit = new FlowStatsEntry(1, Y_MAIN_REVERSE_COOKIE.getValue(), 13, 14, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(yReverseTransit)));
        validateFlowStats(yReverseTransit, Y_MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false, true);
    }

    @Test
    public void flowVlanStatsTest() {
        Flow flow = createFlowWithProtectedPath();
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getReversePath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getProtectedForwardPath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getProtectedReversePath(), flow.getVlanStatistics());

        FlowStatsEntry vlanForwardStats1 = new FlowStatsEntry(1, STAT_VLAN_FORWARD_COOKIE_1.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(vlanForwardStats1)));
        validateStatVlan(vlanForwardStats1, STAT_VLAN_FORWARD_COOKIE_1, SWITCH_ID_1);

        FlowStatsEntry vlanForwardStats2 = new FlowStatsEntry(1, STAT_VLAN_FORWARD_COOKIE_2.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(vlanForwardStats2)));
        validateStatVlan(vlanForwardStats2, STAT_VLAN_FORWARD_COOKIE_2, SWITCH_ID_1);

        FlowStatsEntry vlanReverseStats1 = new FlowStatsEntry(1, STAT_VLAN_REVERSE_COOKIE_1.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(vlanReverseStats1)));
        validateStatVlan(vlanReverseStats1, STAT_VLAN_REVERSE_COOKIE_1, SWITCH_ID_3);

        FlowStatsEntry vlanReverseStats2 = new FlowStatsEntry(1, STAT_VLAN_REVERSE_COOKIE_2.getValue(), 13, 14, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(vlanReverseStats2)));
        validateStatVlan(vlanReverseStats2, STAT_VLAN_REVERSE_COOKIE_2, SWITCH_ID_3);
    }

    @Test
    public void flowStatsMirrorTest() {
        // FLow without mirrors
        Flow flow = createFlow();
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics(), false, false);
        sendUpdateFlowPathInfo(flow.getReversePath(), flow.getVlanStatistics(), false, false);

        // Flow has no mirrors. Ingress rule generates ingress metrics
        FlowStatsEntry ingressForwardStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(ingressForwardStats)));
        validateFlowStats(ingressForwardStats, MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, false);

        // Flow has no mirrors. Egress rule generates egress metrics
        FlowStatsEntry egressReverseStats = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(egressReverseStats)));
        validateFlowStats(egressReverseStats, MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, true);

        // Add mirrors to the flow
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics(), true, true);
        sendUpdateFlowPathInfo(flow.getReversePath(), flow.getVlanStatistics(), true, true);

        // Flow has mirrors. Ingress rule generates only raw metrics
        FlowStatsEntry ingressForwardRawStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 9, 10, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(ingressForwardRawStats)));
        validateFlowStats(ingressForwardRawStats, MAIN_FORWARD_COOKIE, SWITCH_ID_1, false, false);

        // Flow has mirrors. Egress rule generates only raw metrics
        FlowStatsEntry egressReverseRawStats = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 13, 14, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(egressReverseRawStats)));
        validateFlowStats(egressReverseRawStats, MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, false);

        // Flow has mirrors. Mirror rule generates ingress metrics
        FlowStatsEntry mirrorForwardStats = new FlowStatsEntry(1, FORWARD_MIRROR_COOKIE.getValue(), 17, 18, 19, 20);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(mirrorForwardStats)));
        validateFlowStats(mirrorForwardStats, FORWARD_MIRROR_COOKIE, SWITCH_ID_1, true, false);

        // Flow has mirrors. Mirror rule generates egress metrics
        FlowStatsEntry mirrorReverseRawStats = new FlowStatsEntry(1, REVERSE_MIRROR_COOKIE.getValue(), 21, 22, 23, 24);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(mirrorReverseRawStats)));
        validateFlowStats(mirrorReverseRawStats, REVERSE_MIRROR_COOKIE, SWITCH_ID_1, false, true);

        // Remove mirrors from the flow
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics(), false, false);
        sendUpdateFlowPathInfo(flow.getReversePath(), flow.getVlanStatistics(), false, false);

        // Flow has no mirrors. Ingress rule generates ingress metrics
        FlowStatsEntry newIngressForwardStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 25, 26, 27, 28);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(newIngressForwardStats)));
        validateFlowStats(newIngressForwardStats, MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, false);

        // Flow has no mirrors. Egress rule generates egress metrics
        FlowStatsEntry newEgressReverseStats = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 29, 30, 31, 32);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(newEgressReverseStats)));
        validateFlowStats(newEgressReverseStats, MAIN_REVERSE_COOKIE, SWITCH_ID_1, false, true);
    }

    @Test
    public void flowStatsSwapPathTest() {
        Flow flow = createFlowWithProtectedPath();
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getReversePath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getProtectedForwardPath(), flow.getVlanStatistics());
        sendUpdateFlowPathInfo(flow.getProtectedReversePath(), flow.getVlanStatistics());

        // check ingress protected rule on switch 1
        FlowStatsEntry protectedForwardStats = new FlowStatsEntry(1, PROTECTED_FORWARD_COOKIE.getValue(), 1, 2, 3, 4);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(protectedForwardStats)));
        validateFlowStats(protectedForwardStats, PROTECTED_FORWARD_COOKIE, SWITCH_ID_1, true, false);

        // check main forward egress rule on switch 3
        FlowStatsEntry mainForwardEgressStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 5, 6, 7, 8);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(mainForwardEgressStats)));
        validateFlowStats(mainForwardEgressStats, MAIN_FORWARD_COOKIE, SWITCH_ID_3, false, true);

        // check main forward transit rule on switch 2
        FlowStatsEntry transitForwardStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 13, 14, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(transitForwardStats)));
        validateFlowStats(transitForwardStats, MAIN_FORWARD_COOKIE, SWITCH_ID_2, false, false);

        // check main reverse transit rule on switch 2
        FlowStatsEntry transitReverseStats = new FlowStatsEntry(1, MAIN_REVERSE_COOKIE.getValue(), 17, 18, 19, 20);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(transitReverseStats)));
        validateFlowStats(transitReverseStats, MAIN_REVERSE_COOKIE, SWITCH_ID_2, false, false);
    }

    private void validateFlowStats(
            FlowStatsEntry flowStats, FlowSegmentCookie cookie, SwitchId switchId,
            boolean includeIngress, boolean includeEgress) {
        validateFlowStats(flowStats, cookie, switchId, includeIngress, includeEgress, false);
    }

    private void validateFlowStats(
            FlowStatsEntry flowStats, FlowSegmentCookie cookie, SwitchId switchId,
            boolean includeIngress, boolean includeEgress, boolean yFlow) {
        int expectedDatapointCount = 3;
        if (includeIngress) {
            expectedDatapointCount += 3;
        }
        if (includeEgress) {
            expectedDatapointCount += 3;
        }

        List<Datapoint> datapoints = pollDatapoints(expectedDatapointCount);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(flowStats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "flow.raw.packets").getValue().longValue());
        assertEquals(flowStats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "flow.raw.bytes").getValue().longValue());
        assertEquals(flowStats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.raw.bits").getValue().longValue());

        if (includeIngress) {
            assertEquals(flowStats.getPacketCount(),
                    datapointMap.get(METRIC_PREFIX + "flow.ingress.packets").getValue().longValue());
            assertEquals(flowStats.getByteCount(),
                    datapointMap.get(METRIC_PREFIX + "flow.ingress.bytes").getValue().longValue());
            assertEquals(flowStats.getByteCount() * 8,
                    datapointMap.get(METRIC_PREFIX + "flow.ingress.bits").getValue().longValue());
        }

        if (includeEgress) {
            assertEquals(flowStats.getPacketCount(),
                    datapointMap.get(METRIC_PREFIX + "flow.packets").getValue().longValue());
            assertEquals(flowStats.getByteCount(),
                    datapointMap.get(METRIC_PREFIX + "flow.bytes").getValue().longValue());
            assertEquals(flowStats.getByteCount() * 8,
                    datapointMap.get(METRIC_PREFIX + "flow.bits").getValue().longValue());
        }

        String direction = cookie.getDirection().name().toLowerCase();
        int expectedRawTagsCount = cookie.isMirror() ? 12 : 9;
        int expectedEndpointTagsCount = yFlow ? 4 : 3;

        datapoints.forEach(datapoint -> {
            switch (datapoint.getMetric()) {
                case METRIC_PREFIX + "flow.raw.packets":
                case METRIC_PREFIX + "flow.raw.bytes":
                case METRIC_PREFIX + "flow.raw.bits":
                    assertEquals(expectedRawTagsCount, datapoint.getTags().size());
                    assertEquals(flowId, datapoint.getTags().get("flowid"));
                    assertEquals(direction, datapoint.getTags().get("direction"));
                    assertEquals(String.valueOf(flowStats.getTableId()), datapoint.getTags().get("tableid"));
                    assertEquals(Long.toString(cookie.getValue()), datapoint.getTags().get("cookie"));
                    assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
                    assertEquals(Integer.toString(flowStats.getOutPort()), datapoint.getTags().get("outPort"));
                    assertEquals(Integer.toString(flowStats.getInPort()), datapoint.getTags().get("inPort"));

                    if (cookie.isMirror()) {
                        assertEquals("true", datapoint.getTags().get("is_flow_satellite"));
                        assertEquals("true", datapoint.getTags().get("is_mirror"));
                        assertEquals("false", datapoint.getTags().get("is_loop"));
                        assertEquals("false", datapoint.getTags().get("is_flowrtt_inject"));
                    } else {
                        assertEquals("false", datapoint.getTags().get("is_flow_satellite"));
                    }
                    break;
                case METRIC_PREFIX + "flow.ingress.packets":
                case METRIC_PREFIX + "flow.ingress.bytes":
                case METRIC_PREFIX + "flow.ingress.bits":
                case METRIC_PREFIX + "flow.packets":
                case METRIC_PREFIX + "flow.bytes":
                case METRIC_PREFIX + "flow.bits":
                    assertEquals(expectedEndpointTagsCount, datapoint.getTags().size());
                    assertEquals(flowId, datapoint.getTags().get("flowid"));
                    assertEquals(direction, datapoint.getTags().get("direction"));
                    if (yFlow) {
                        assertEquals(Y_FLOW_ID, datapoint.getTags().get("y_flow_id"));
                    }
                    break;
                default:
                    throw new AssertionError(format("Unknown metric: %s", datapoint.getMetric()));
            }
        });
    }

    private void validateStatVlan(FlowStatsEntry flowStats, FlowSegmentCookie cookie, SwitchId switchId) {
        List<Datapoint> datapoints = pollDatapoints(3);
        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(flowStats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "flow.vlan.packets").getValue().longValue());
        assertEquals(flowStats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "flow.vlan.bytes").getValue().longValue());
        assertEquals(flowStats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.vlan.bits").getValue().longValue());

        String direction = cookie.getDirection().name().toLowerCase();

        datapoints.forEach(datapoint -> {
            switch (datapoint.getMetric()) {
                case METRIC_PREFIX + "flow.vlan.packets":
                case METRIC_PREFIX + "flow.vlan.bytes":
                case METRIC_PREFIX + "flow.vlan.bits":
                    assertEquals(6, datapoint.getTags().size());
                    assertEquals(flowId, datapoint.getTags().get("flowid"));
                    assertEquals(direction, datapoint.getTags().get("direction"));
                    assertEquals(Long.toString(cookie.getValue()), datapoint.getTags().get("cookie"));
                    assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
                    assertEquals(Integer.toString(cookie.getStatsVlan()), datapoint.getTags().get("vlan"));
                    assertEquals(Integer.toString(flowStats.getInPort()), datapoint.getTags().get("inPort"));
                    break;
                default:
                    throw new AssertionError(format("Unknown metric: %s", datapoint.getMetric()));
            }
        });
    }

    @Test
    @Ignore
    public void flowLldpStatsTest() {
        long lldpCookie = 1;
        FlowStatsEntry stats = new FlowStatsEntry(1, lldpCookie, 450, 550L, 10, 10);

        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(stats)));

        List<Datapoint> datapoints = pollDatapoints(3);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(stats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "flow.raw.packets").getValue().longValue());
        assertEquals(stats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "flow.raw.bytes").getValue().longValue());
        assertEquals(stats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "flow.raw.bits").getValue().longValue());

        datapoints.forEach(datapoint -> {
            assertEquals(7, datapoint.getTags().size());
            assertEquals("reverse", datapoint.getTags().get("direction"));
            assertEquals(String.valueOf(stats.getTableId()), datapoint.getTags().get("tableid"));
            assertEquals(String.valueOf(lldpCookie), datapoint.getTags().get("cookie"));
            assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals("10", datapoint.getTags().get("outPort"));
            assertEquals("10", datapoint.getTags().get("inPort"));
            assertEquals("unknown", datapoint.getTags().get("flowid"));
        });
    }

    @Test
    public void flowAttendantRulesStatsTest() {
        Flow flow = createFlow();

        FlowSegmentCookie server42IngressCookie = MAIN_FORWARD_COOKIE
                .toBuilder().type(CookieType.SERVER_42_FLOW_RTT_INGRESS).build();

        FlowEndpoint ingress = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowStatsEntry measure0 = new FlowStatsEntry(
                0, server42IngressCookie.getValue(), 0, 0, ingress.getPortNumber(), ingress.getPortNumber() + 1);
        FlowStatsEntry measure1 = new FlowStatsEntry(
                0, server42IngressCookie.getValue(), 1, 200, ingress.getPortNumber(), ingress.getPortNumber() + 1);
        FlowStatsEntry measure2 = new FlowStatsEntry(
                0, server42IngressCookie.getValue(), 2, 300, ingress.getPortNumber(), ingress.getPortNumber() + 1);

        sendStatsMessage(new FlowStatsData(ingress.getSwitchId(), Collections.singletonList(measure0)));

        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());

        sendStatsMessage(new FlowStatsData(ingress.getSwitchId(), Collections.singletonList(measure1)));

        sendRemoveFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());

        sendStatsMessage(new FlowStatsData(ingress.getSwitchId(), Collections.singletonList(measure2)));

        List<Datapoint> datapoints = pollDatapoints(9);
        List<Datapoint> rawPacketsMetric = datapoints.stream()
                .filter(entry -> (METRIC_PREFIX + "flow.raw.packets").equals(entry.getMetric()))
                .collect(Collectors.toList());

        Assert.assertEquals(3, rawPacketsMetric.size());
        for (Datapoint entry : rawPacketsMetric) {
            Map<String, String> tags = entry.getTags();
            Assert.assertEquals(CookieType.SERVER_42_FLOW_RTT_INGRESS.name().toLowerCase(), tags.get("type"));

            if (Objects.equals(0, entry.getValue())) {
                Assert.assertEquals("unknown", tags.get("flowid"));
            } else if (Objects.equals(1, entry.getValue())) {
                Assert.assertEquals(flowId, tags.get("flowid"));
            } else if (Objects.equals(2, entry.getValue())) {
                Assert.assertEquals("unknown", tags.get("flowid"));
            } else {
                Assert.fail(format("Unexpected metric value: %s", entry));
            }
        }
    }

    @Test
    public void systemRulesStatsTest() {
        FlowStatsEntry systemRuleStats = new FlowStatsEntry(1, VERIFICATION_BROADCAST_RULE_COOKIE, 100L, 200L, 10, 10);

        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(systemRuleStats)));

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
            assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(Cookie.toString(VERIFICATION_BROADCAST_RULE_COOKIE), datapoint.getTags().get("cookieHex"));
        });
    }

    @Test
    public void flowRttTest() {
        FlowRttStatsData flowRttStatsData = FlowRttStatsData.builder()
                .flowId(flowId)
                .direction("forward")
                .t0(123456789_987654321L)
                .t1(123456789_987654321L + 1)
                .build();

        long seconds = (123456789_987654321L >> 32);
        long nanoseconds = (123456789_987654321L & 0xFFFFFFFFL);
        long timestamp = TimeUnit.NANOSECONDS.toMillis(TimeUnit.SECONDS.toNanos(seconds) + nanoseconds);

        InfoMessage infoMessage = new InfoMessage(flowRttStatsData, timestamp, UUID.randomUUID().toString(),
                Destination.WFM_STATS, null);

        sendMessage(infoMessage, statsTopologyConfig.getServer42StatsFlowRttTopic());

        List<Datapoint> datapoints = pollDatapoints(1);

        assertEquals(1, datapoints.size());

        Datapoint datapoint = datapoints.get(0);

        assertEquals(METRIC_PREFIX + "flow.rtt", datapoint.getMetric());
        assertEquals(1, datapoint.getValue());
        assertEquals("forward", datapoint.getTags().get("direction"));
        assertEquals(flowId, datapoint.getTags().get("flowid"));
        assertEquals(timestamp, datapoint.getTime().longValue());
    }

    @Test
    public void tableStatsTest() {
        TableStatsEntry entry = TableStatsEntry.builder()
                .tableId(1)
                .activeEntries(100)
                .lookupCount(2000)
                .matchedCount(1900)
                .build();
        SwitchTableStatsData tableStatsData = SwitchTableStatsData.builder()
                .switchId(SWITCH_ID_1)
                .tableStatsEntries(ImmutableList.of(entry))
                .build();

        sendStatsMessage(tableStatsData);

        List<Datapoint> datapoints = pollDatapoints(4);

        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(entry.getActiveEntries(),
                datapointMap.get(METRIC_PREFIX + "switch.table.active").getValue().intValue());
        assertEquals(entry.getLookupCount(),
                datapointMap.get(METRIC_PREFIX + "switch.table.lookup").getValue().longValue());
        assertEquals(entry.getMatchedCount(),
                datapointMap.get(METRIC_PREFIX + "switch.table.matched").getValue().longValue());
        assertEquals(entry.getLookupCount() - entry.getMatchedCount(),
                datapointMap.get(METRIC_PREFIX + "switch.table.missed").getValue().longValue());


        datapoints.forEach(datapoint -> {
            assertEquals(SWITCH_ID_1.toOtsdFormat(), datapoint.getTags().get("switchid"));
            assertEquals(entry.getTableId(), Integer.parseInt(datapoint.getTags().get("tableid")));
            assertEquals(timestamp, datapoint.getTime().longValue());
        });
    }

    private Flow createOneSwitchFlow(SwitchId switchId) {
        Switch sw = createSwitch(switchId);

        Flow flow = new TestFlowBuilder(flowId)
                .srcSwitch(sw)
                .srcPort(1)
                .srcVlan(5)
                .destSwitch(sw)
                .destPort(2)
                .destVlan(5)
                .unmaskedCookie(MAIN_COOKIE)
                .forwardMeterId(456)
                .reverseMeterId(457)
                .forwardTransitEncapsulationId(ENCAPSULATION_ID)
                .reverseTransitEncapsulationId(ENCAPSULATION_ID + 1)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .vlanStatistics(STAT_VLANS)
                .build();

        flowRepository.add(flow);
        return flow;
    }

    private Flow createFlow() {
        Switch srcSw = createSwitch(SWITCH_ID_1);
        Switch dstSw = createSwitch(SWITCH_ID_3);

        Flow flow = new TestFlowBuilder(flowId)
                .srcSwitch(srcSw)
                .srcPort(PORT_1)
                .srcVlan(5)
                .addTransitionEndpoint(srcSw, PORT_2)
                .addTransitionEndpoint(dstSw, PORT_1)
                .unmaskedCookie(MAIN_COOKIE)
                .forwardMeterId(456)
                .forwardTransitEncapsulationId(ENCAPSULATION_ID)
                .reverseMeterId(457)
                .reverseTransitEncapsulationId(ENCAPSULATION_ID + 1)
                .destSwitch(dstSw)
                .destPort(PORT_3)
                .destVlan(5)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .vlanStatistics(STAT_VLANS)
                .build();
        flowRepository.add(flow);
        return flow;
    }

    private Flow createFlowWithProtectedPath() {
        Switch srcSw = createSwitch(SWITCH_ID_1);
        Switch transitSw = createSwitch(SWITCH_ID_2);
        Switch dstSw = createSwitch(SWITCH_ID_3);

        Flow flow = new TestFlowBuilder(flowId)
                .srcSwitch(srcSw)
                .srcPort(PORT_1)
                .srcVlan(5)
                .addTransitionEndpoint(srcSw, PORT_2)
                .addTransitionEndpoint(transitSw, PORT_1)
                .addTransitionEndpoint(transitSw, PORT_2)
                .addTransitionEndpoint(dstSw, PORT_1)
                .unmaskedCookie(MAIN_COOKIE)
                .forwardMeterId(456)
                .forwardTransitEncapsulationId(ENCAPSULATION_ID)
                .reverseMeterId(457)
                .reverseTransitEncapsulationId(ENCAPSULATION_ID + 1)
                .addProtectedTransitionEndpoint(srcSw, PORT_3)
                .addProtectedTransitionEndpoint(dstSw, PORT_2)
                .protectedUnmaskedCookie(PROTECTED_COOKIE)
                .protectedForwardMeterId(458)
                .protectedForwardTransitEncapsulationId(ENCAPSULATION_ID + 2)
                .protectedReverseMeterId(459)
                .protectedReverseTransitEncapsulationId(ENCAPSULATION_ID + 3)
                .destSwitch(dstSw)
                .destPort(PORT_3)
                .destVlan(5)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .vlanStatistics(STAT_VLANS)
                .build();
        flowRepository.add(flow);
        return flow;
    }

    private YFlow buildYFlow(SwitchId sharedSwitchId, SwitchId yPointSwitchId) {
        return YFlow.builder()
                .yFlowId(Y_FLOW_ID)
                .sharedEndpoint(new SharedEndpoint(sharedSwitchId, PORT_1))
                .yPoint(yPointSwitchId)
                .meterId(Y_POINT_METER_ID)
                .sharedEndpointMeterId(SHARED_POINT_METER_ID)
                .status(FlowStatus.UP)
                .build();
    }

    private YFlow addSubFlowAndCreate(YFlow yFlow, Flow flow) {
        yFlow.setSubFlows(Sets.newHashSet(
                YSubFlow.builder()
                        .sharedEndpointVlan(flow.getSrcVlan())
                        .sharedEndpointInnerVlan(flow.getSrcInnerVlan())
                        .endpointSwitchId(flow.getDestSwitchId())
                        .endpointPort(flow.getDestPort())
                        .endpointVlan(flow.getDestVlan())
                        .endpointInnerVlan(flow.getDestInnerVlan())
                        .flow(flow)
                        .yFlow(yFlow)
                        .build()));
        YFlowRepository yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        yFlowRepository.add(yFlow);
        return yFlow;
    }


    private Flow createYSubFlow() {
        Switch sharedSw = createSwitch(SWITCH_ID_1);
        Switch yPointSw = createSwitch(SWITCH_ID_2);
        Switch dstSw = createSwitch(SWITCH_ID_3);

        YFlow yFlow = buildYFlow(sharedSw.getSwitchId(), yPointSw.getSwitchId());
        Flow flow = buildSubFlowBase(yFlow)
                .srcSwitch(sharedSw)
                .addTransitionEndpoint(sharedSw, PORT_2)
                .addTransitionEndpoint(yPointSw, PORT_2)
                .addTransitionEndpoint(yPointSw, PORT_3)
                .addTransitionEndpoint(dstSw, PORT_3)
                .destSwitch(dstSw)
                .build();

        flowRepository.add(flow);
        addSubFlowAndCreate(yFlow, flow);
        return flow;
    }

    private Flow createVSubFlow() {
        Switch sharedSw = createSwitch(SWITCH_ID_1);
        Switch transitSw = createSwitch(SWITCH_ID_2);
        Switch dstSw = createSwitch(SWITCH_ID_3);

        YFlow yFlow = buildYFlow(sharedSw.getSwitchId(), sharedSw.getSwitchId());
        Flow flow = buildSubFlowBase(yFlow)
                .srcSwitch(sharedSw)
                .addTransitionEndpoint(sharedSw, PORT_2)
                .addTransitionEndpoint(transitSw, PORT_2)
                .addTransitionEndpoint(transitSw, PORT_3)
                .addTransitionEndpoint(dstSw, PORT_3)
                .destSwitch(dstSw)
                .build();

        flowRepository.add(flow);
        addSubFlowAndCreate(yFlow, flow);
        return flow;
    }

    private Flow createFlatSubFlow() {
        Switch sharedSw = createSwitch(SWITCH_ID_1);
        Switch transitSw = createSwitch(SWITCH_ID_2);
        Switch dstSw = createSwitch(SWITCH_ID_3);

        YFlow yFlow = buildYFlow(sharedSw.getSwitchId(), dstSw.getSwitchId());
        Flow flow = buildSubFlowBase(yFlow)
                .srcSwitch(sharedSw)
                .addTransitionEndpoint(sharedSw, PORT_2)
                .addTransitionEndpoint(transitSw, PORT_2)
                .addTransitionEndpoint(transitSw, PORT_3)
                .addTransitionEndpoint(dstSw, PORT_3)
                .destSwitch(dstSw)
                .build();

        flowRepository.add(flow);
        addSubFlowAndCreate(yFlow, flow);
        return flow;
    }

    private TestFlowBuilder buildSubFlowBase(YFlow yFlow) {
        return new TestFlowBuilder(flowId)
                .yFlowId(Y_FLOW_ID)
                .yFlow(yFlow)
                .srcPort(PORT_1)
                .srcVlan(5)
                .unmaskedCookie(MAIN_COOKIE)
                .forwardMeterId(456)
                .forwardTransitEncapsulationId(ENCAPSULATION_ID)
                .reverseMeterId(457)
                .reverseTransitEncapsulationId(ENCAPSULATION_ID + 1)
                .destPort(PORT_4)
                .destVlan(5)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .vlanStatistics(STAT_VLANS);
    }

    private static Switch createSwitch(SwitchId switchId) {
        Switch sw = Switch.builder().switchId(switchId).build();
        switchRepository.add(sw);
        return sw;
    }

    private void sendStatsMessage(InfoData infoData) {
        InfoMessage infoMessage = new InfoMessage(infoData, timestamp, UUID.randomUUID().toString(),
                Destination.WFM_STATS, null);
        sendMessage(infoMessage, statsTopologyConfig.getKafkaStatsTopic());
    }

    private void sendRemoveFlowPathInfo(FlowPath flowPath, Set<Integer> vlanStatistics) {
        sendRemoveFlowPathInfo(flowPath, vlanStatistics, flowPath.hasIngressMirror(), flowPath.hasEgressMirror());
    }

    private void sendRemoveFlowPathInfo(
            FlowPath flowPath, Set<Integer> vlanStatistics, boolean ingressMirror, boolean egressMirror) {
        RemoveFlowPathInfo pathInfo = new RemoveFlowPathInfo(
                flowPath.getFlowId(), null, null, flowPath.getCookie(), flowPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(flowPath.getFlow(), flowPath), vlanStatistics, ingressMirror,
                egressMirror);
        sendNotification(pathInfo);
    }


    private void sendUpdateFlowPathInfo(FlowPath flowPath, Set<Integer> vlanStatistics) {
        sendUpdateFlowPathInfo(flowPath, vlanStatistics, flowPath.hasIngressMirror(), flowPath.hasEgressMirror());
    }

    private void sendUpdateFlowPathInfo(
            FlowPath flowPath, Set<Integer> vlanStatistics, boolean ingressMirror, boolean egressMirror) {
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flowPath.getFlowId(), null, null, flowPath.getCookie(), flowPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(flowPath.getFlow(), flowPath), vlanStatistics, ingressMirror,
                egressMirror);
        sendNotification(pathInfo);
    }

    private void sendUpdateSubFlowPathInfo(FlowPath flowPath, Flow flow) {
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flowPath.getFlowId(), flow.getYFlowId(), flow.getYPointSwitchId(), flowPath.getCookie(),
                flowPath.getMeterId(), FlowPathMapper.INSTANCE.mapToPathNodes(flowPath.getFlow(), flowPath),
                flow.getVlanStatistics(), false, false);
        sendNotification(pathInfo);
    }

    private void sendUpdateYFlowPathInfo(YFlow yflow) {
        UpdateYFlowStatsInfo info = new UpdateYFlowStatsInfo(yflow.getYFlowId(),
                new YFlowEndpointResources(yflow.getSharedEndpoint().getSwitchId(), yflow.getSharedEndpointMeterId()),
                new YFlowEndpointResources(yflow.getYPoint(), yflow.getMeterId()), null);
        sendNotification(info);
    }

    private void sendRemoveYFlowPathInfo(YFlow yflow) {
        RemoveYFlowStatsInfo info = new RemoveYFlowStatsInfo(yflow.getYFlowId(),
                new YFlowEndpointResources(yflow.getSharedEndpoint().getSwitchId(), yflow.getSharedEndpointMeterId()),
                new YFlowEndpointResources(yflow.getYPoint(), yflow.getMeterId()), null);
        sendNotification(info);
    }

    private void sendNotification(StatsNotification notification) {
        InfoMessage infoMessage = new InfoMessage(notification, timestamp, UUID.randomUUID().toString(), null, null);
        sendMessage(infoMessage, statsTopologyConfig.getFlowStatsNotifyTopic());
    }

    private void sendMessage(Object object, String topic) {
        String request = null;
        try {
            request = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            fail("Unexpected error: " + e.getMessage());
        }
        kProducer.pushMessage(topic, request);
    }

    private List<Datapoint> pollDatapoints(int expectedDatapointCount) {
        List<Datapoint> datapoints = new ArrayList<>();

        for (int i = 0; i < expectedDatapointCount; i++) {
            ConsumerRecord<String, String> record = null;
            try {
                record = otsdbConsumer.pollMessage(POLL_TIMEOUT);
                if (record == null) {
                    throw new AssertionError(format(POLL_DATAPOINT_ASSERT_MESSAGE,
                            expectedDatapointCount, datapoints.size()));
                }
                Datapoint datapoint = objectMapper.readValue(record.value(), Datapoint.class);
                datapoints.add(datapoint);
            } catch (InterruptedException e) {
                throw new AssertionError(format(POLL_DATAPOINT_ASSERT_MESSAGE,
                        expectedDatapointCount, datapoints.size()));
            } catch (IOException e) {
                throw new AssertionError(format("Could not parse datapoint object: '%s'", record.value()));
            }
        }
        try {
            // ensure that we received exact expected count of records
            ConsumerRecord<String, String> record = otsdbConsumer.pollMessage(POLL_TIMEOUT);
            if (record != null) {
                throw new AssertionError(format(
                        "Got more then %d records. Extra record '%s'", expectedDatapointCount, record));
            }
        } catch (InterruptedException e) {
            return datapoints;
        }
        return datapoints;
    }

    private void assertMetric(long expected, String metricName, Map<String, Datapoint> datapointMap) {
        assertEquals(expected, datapointMap.get(METRIC_PREFIX + metricName).getValue().longValue());
    }

    private Map<String, Datapoint> createDatapointMap(List<Datapoint> datapoints) {
        return datapoints
                .stream()
                .collect(Collectors.toMap(Datapoint::getMetric, Function.identity()));
    }
}
