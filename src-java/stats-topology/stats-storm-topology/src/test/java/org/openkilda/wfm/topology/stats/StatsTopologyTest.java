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

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.storm.utils.Utils.sleep;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.openkilda.model.FlowPathDirection.FORWARD;
import static org.openkilda.model.FlowPathDirection.REVERSE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlowPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildSegments;
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
import org.openkilda.messaging.info.stats.GroupStatsData;
import org.openkilda.messaging.info.stats.GroupStatsEntry;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveHaFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveYFlowStatsInfo;
import org.openkilda.messaging.info.stats.StatsNotification;
import org.openkilda.messaging.info.stats.SwitchTableStatsData;
import org.openkilda.messaging.info.stats.TableStatsEntry;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateHaFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateYFlowStatsInfo;
import org.openkilda.messaging.model.grpc.PacketInOutStatsDto;
import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

@Slf4j
public class StatsTopologyTest extends AbstractStormTest {

    private static final long timestamp = System.currentTimeMillis();
    private static final int POLL_TIMEOUT = 100;
    private static final String POLL_DATAPOINT_ASSERT_MESSAGE = "Could not poll all %d datapoints, got only %d records";
    private static final String METRIC_PREFIX = "kilda.";
    private static final int ENCAPSULATION_ID = 123;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String COMPONENT_NAME = "stats";
    public static final String RUN_ID = "blue";
    public static final String ROOT_NODE = "kilda";
    private static final int STAT_VLAN_1 = 4;
    private static final int VLAN_1 = 5;
    public static final int VLAN_2 = 6;
    private static final int INNER_VLAN_1 = 8;
    private static final int STAT_VLAN_2 = 5;
    public static final int ZERO_INNER_VLAN = 0;
    public static final int INNER_VLAN_2 = 9;

    public static final HashSet<Integer> STAT_VLANS = Sets.newHashSet(STAT_VLAN_1, STAT_VLAN_2);
    public static final int PRIORITY_1 = 13;
    public static final String DESCRIPTION_1 = "description_1";
    public static final String DESCRIPTION_2 = "description_2";
    private static final long LATENCY_1 = 1;
    private static final long LATENCY_2 = 2;
    private static final int BANDWIDTH_1 = 1000;
    public static final String SUB_FLOW_ID_1 = "test_sub_flow_1";
    public static final String SUB_FLOW_ID_2 = "test_sub_flow_2";
    public static final String SUB_FLOW_ID_SHARED = "shared";
    private static InMemoryGraphPersistenceManager persistenceManager;
    private static StatsTopologyConfig statsTopologyConfig;
    private static TestKafkaConsumer otsdbConsumer;
    private static FlowRepository flowRepository;
    private static HaFlowRepository haFlowRepository;
    private static HaSubFlowRepository haSubFlowRepository;
    private static HaFlowPathRepository haFlowPathRepository;
    private static FlowPathRepository flowPathRepository;
    private static YFlowRepository yFlowRepository;
    private static SwitchRepository switchRepository;
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1L);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2L);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3L);
    public static final SwitchId SWITCH_ID_4 = new SwitchId(4L);
    private static final SwitchId SWITCH_ID_5 = new SwitchId(5L);
    public static final SwitchId SWITCH_ID_6 = new SwitchId(6L);
    public static final SwitchId SWITCH_ID_7 = new SwitchId(7L);
    public static final PathId PATH_ID_1 = new PathId("path_1");
    public static final PathId PATH_ID_2 = new PathId("path_2");
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int PORT_3 = 3;
    private static final int PORT_4 = 4;
    public static final MeterId METER_ID_HA_FLOW_PATH_FORWARD = new MeterId(11);
    public static final MeterId METER_ID_HA_FLOW_PATH_REVERSE = new MeterId(14);
    public static final MeterId METER_ID_REVERSE_SUB_PATH_1 = new MeterId(14);
    public static final MeterId METER_ID_REVERSE_SUB_PATH_2 = new MeterId(14);
    public static final GroupId GROUP_ID_1 = new GroupId(15);
    public static final PathId SUB_PATH_ID_1 = new PathId("sub_path_id_1");
    public static final PathId SUB_PATH_ID_2 = new PathId("sub_path_id_2");
    public static final PathId SUB_PATH_ID_3 = new PathId("sub_path_id_3");
    public static final PathId SUB_PATH_ID_4 = new PathId("sub_path_id_4");
    private static final MeterId SHARED_POINT_METER_ID = new MeterId(4);
    private static final MeterId Y_POINT_METER_ID = new MeterId(5);
    private static final FlowSegmentCookie COOKIE_FORWARD = FlowSegmentCookie.builder().flowEffectiveId(1)
            .direction(FlowPathDirection.FORWARD).build();
    private static final FlowSegmentCookie COOKIE_FORWARD_SUBFLOW_1 = COOKIE_FORWARD.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1).build();
    private static final FlowSegmentCookie COOKIE_FORWARD_SUBFLOW_2 = COOKIE_FORWARD.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2).build();
    private static final FlowSegmentCookie COOKIE_REVERSE = FlowSegmentCookie.builder().flowEffectiveId(2)
            .direction(FlowPathDirection.REVERSE).build();

    private static final FlowSegmentCookie COOKIE_REVERSE_SUBFLOW_1 = COOKIE_REVERSE.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1).build();
    private static final FlowSegmentCookie COOKIE_REVERSE_SUBFLOW_2 = COOKIE_REVERSE.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2).build();
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
    private static final String FLOW_ID = "f253423454343";
    private static final String Y_FLOW_ID = "Y_flow_1";
    private static final String HA_FLOW_ID_1 = "test_ha_flow_1";
    private static final String HA_FLOW_ID_2 = "test_ha_flow_2";
    private static final String HA_FLOW_ID_3 = "test_ha_flow_3";
    private static final String HA_FLOW_ID_4 = "test_ha_flow_4";
    private static final String HA_FLOW_ID_5 = "test_ha_flow_5";

    @BeforeAll
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
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        haSubFlowRepository = persistenceManager.getRepositoryFactory().createHaSubFlowRepository();
        haFlowPathRepository = persistenceManager.getRepositoryFactory().createHaFlowPathRepository();
        yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        sleep(TOPOLOGY_START_TIMEOUT);
    }

    @AfterAll
    public static void teardownOnce() throws Exception {
        otsdbConsumer.wakeup();
        otsdbConsumer.join();

        AbstractStormTest.stopZooKafkaAndStorm();
    }

    @BeforeEach
    public void setup() {
        otsdbConsumer.clear();
        // need clear data in CacheBolt
        for (Flow flow : flowRepository.findAll()) {
            flow.getPaths().forEach(path -> sendRemoveFlowPathInfo(path, flow.getVlanStatistics(),
                    flow.getYFlowId(), flow.getYPointSwitchId()));
        }
        for (YFlow yFlow : yFlowRepository.findAll()) {
            sendRemoveYFlowPathInfo(yFlow);
        }
        for (HaFlow haFlow : haFlowRepository.findAll()) {
            haFlow.getPaths().forEach(haFlowPath -> haFlowPath.getSubPaths()
                    .forEach(flowPath -> sendRemoveHaFlowPathInfo(flowPath, haFlow)));
        }

        persistenceManager.getInMemoryImplementation().purgeData();
    }

    @Test
    public void portStatsTest() {
        final SwitchId switchId = new SwitchId(1L);
        int portCount = 52;

        final List<PortStatsEntry> entries = IntStream.range(1, portCount + 1).boxed().map(port -> {
            int baseCount = port * 20;
            return new PortStatsEntry(port, baseCount, baseCount + 1, baseCount + 2,
                    baseCount + 3, baseCount + 4, baseCount + 5, baseCount + 6,
                    baseCount + 7, baseCount + 8, baseCount + 9, baseCount + 10,
                    baseCount + 11);
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
            assertEquals(FLOW_ID, datapoint.getTags().get("flowid"));
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
    @Disabled
    public void flowLldpStatsTest() {
        long lldpCookie = 1;
        FlowStatsEntry stats = new FlowStatsEntry(1, lldpCookie,
                450, 550L, 10, 10);

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
                0, server42IngressCookie.getValue(), 0, 0, ingress.getPortNumber(),
                ingress.getPortNumber() + 1);
        FlowStatsEntry measure1 = new FlowStatsEntry(
                0, server42IngressCookie.getValue(), 1, 200, ingress.getPortNumber(),
                ingress.getPortNumber() + 1);
        FlowStatsEntry measure2 = new FlowStatsEntry(
                0, server42IngressCookie.getValue(), 2, 300, ingress.getPortNumber(),
                ingress.getPortNumber() + 1);

        sendStatsMessage(new FlowStatsData(ingress.getSwitchId(), Collections.singletonList(measure0)));

        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());

        sendStatsMessage(new FlowStatsData(ingress.getSwitchId(), Collections.singletonList(measure1)));

        sendRemoveFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics(), null, null);

        sendStatsMessage(new FlowStatsData(ingress.getSwitchId(), Collections.singletonList(measure2)));

        List<Datapoint> datapoints = pollDatapoints(9);
        List<Datapoint> rawPacketsMetric = datapoints.stream()
                .filter(entry -> (METRIC_PREFIX + "flow.raw.packets").equals(entry.getMetric()))
                .collect(toList());

        assertEquals(3, rawPacketsMetric.size());
        for (Datapoint entry : rawPacketsMetric) {
            Map<String, String> tags = entry.getTags();
            assertEquals(CookieType.SERVER_42_FLOW_RTT_INGRESS.name().toLowerCase(), tags.get("type"));

            if (Objects.equals(0, entry.getValue())) {
                assertEquals("unknown", tags.get("flowid"));
            } else if (Objects.equals(1, entry.getValue())) {
                assertEquals(FLOW_ID, tags.get("flowid"));
            } else if (Objects.equals(2, entry.getValue())) {
                assertEquals("unknown", tags.get("flowid"));
            } else {
                fail(format("Unexpected metric value: %s", entry));
            }
        }
    }

    @Test
    public void systemRulesStatsTest() {
        FlowStatsEntry systemRuleStats = new FlowStatsEntry(1, VERIFICATION_BROADCAST_RULE_COOKIE,
                100L, 200L, 10, 10);

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
                .flowId(FLOW_ID)
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
        assertEquals(FLOW_ID, datapoint.getTags().get("flowid"));
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

    @Test
    public void flowWithTwoSwitchesStatsTest() {
        Flow flow = createFlow();
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, flow.getForwardPath().getCookie().getValue(),
                160L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateFlowStats(forwardIngress, flow.getForwardPath().getCookie(), SWITCH_ID_1, true, false);
    }

    /**
     * 4 switch ha-flow flow-stats test.
     * ''''''''/-4--5
     * 1--2--3
     * '''''''\----6
     */
    @Test
    public void haFlowFakeStatsTest() {
        HaFlow haFlow = createHaFlow1();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardFake = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue() + 1,
                160L, 300L, 10, 10);
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                160L, 300L, 10, 10);

        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Lists.newArrayList(forwardFake, forwardIngress)));

        List<Datapoint> datapoints = pollDatapoints(9);
        assertEquals(3,
                datapoints.stream().filter(dataPoint -> dataPoint.getMetric().contains(METRIC_PREFIX + "flow.raw."))
                        .count(), "Expected 3 flow.raw. metrics");
        assertEquals(3,
                datapoints.stream().filter(dataPoint -> dataPoint.getMetric().contains(METRIC_PREFIX + "haflow.raw."))
                        .count(), "Expected 3 haflow.raw. metrics");
        assertEquals(3,
                datapoints.stream().filter(dataPoint -> dataPoint.getMetric().contains(METRIC_PREFIX + "haflow.raw."))
                        .count(), "Expected 3 haflow.ingress. metrics");
    }

    /**
     * 4 switch ha-flow flow-stats update test.
     * ''''''''/-4--5
     * 1--2--3
     * '''''''\----6
     */
    @Test
    public void haFlowStatsUpdateTest() {
        Switch switch1 = createSwitch(SWITCH_ID_1); //shared,
        Switch switch2 = createSwitch(SWITCH_ID_2); //transit,
        Switch switch3 = createSwitch(SWITCH_ID_3); //y-point
        Switch switch4 = createSwitch(SWITCH_ID_4); //transit
        Switch switch5 = createSwitch(SWITCH_ID_5); // endpoint
        Switch switch6 = createSwitch(SWITCH_ID_6); // endpoint


        HaFlow haFlow = buildHaFlowWithoutPersistence(switch1, switch2, switch3, switch4, switch5, switch6,
                1, 2, "", "");

        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                160L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardIngress, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                true, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardTransit1 = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                150L, 290L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardTransit1)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardTransit1, haFlow.getForwardPath().getCookie(), SWITCH_ID_2,
                false, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                140L, 280L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardYPoint, haFlow.getForwardPath().getCookie(), SWITCH_ID_3,
                false, false, true, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardTransitPoint2 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                130L, 270L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_4, Collections.singletonList(forwardTransitPoint2)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardTransitPoint2, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_4,
                false, false, false, SUB_FLOW_ID_1);

        FlowStatsEntry forwardEgressPoint1 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                120L, 260L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_5, Collections.singletonList(forwardEgressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardEgressPoint1, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_5,
                false, true, false, SUB_FLOW_ID_1);

        FlowStatsEntry forwardEgressPoint2 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_2.getValue(),
                110L, 250L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_6, Collections.singletonList(forwardEgressPoint2)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardEgressPoint2, COOKIE_FORWARD_SUBFLOW_2, SWITCH_ID_6,
                false, true, false, SUB_FLOW_ID_2);

        // <-----------------HERE WE ARE GOING TO UPDATE HA-FLOW ------------------->

        Switch switch7 = createSwitch(SWITCH_ID_7); // sharedPoint
        HaFlow haFlowUpdated = buildHaFlowWithoutPersistence(switch7, switch2, switch3, switch4, switch5, switch6,
                10, 20, "_updated", "_updated");

        sendUpdateHaFlowPathInfo(haFlowUpdated);
        sendRemoveHaFlowPathInfo(haFlow);

        FlowStatsEntry forwardIngressUpdated = new FlowStatsEntry(1,
                haFlowUpdated.getForwardPath().getCookie().getValue(),
                160L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_7, Collections.singletonList(forwardIngressUpdated)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardIngressUpdated,
                haFlowUpdated.getForwardPath().getCookie(), SWITCH_ID_7,
                true, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardTransit1Updated = new FlowStatsEntry(
                1, haFlowUpdated.getForwardPath().getCookie().getValue(), 150L, 290L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardTransit1Updated)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardTransit1Updated, haFlowUpdated.getForwardPath().getCookie(),
                SWITCH_ID_2, false, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardYPointUpdated = new FlowStatsEntry(
                1, haFlowUpdated.getForwardPath().getCookie().getValue(), 140L, 280L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(forwardYPointUpdated)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardYPointUpdated, haFlowUpdated.getForwardPath().getCookie(), SWITCH_ID_3,
                false, false, true, SUB_FLOW_ID_SHARED);

        FlowSegmentCookie cookieForwardSubflowUpdated1 = haFlowUpdated.getForwardPath()
                .getCookie().toBuilder().subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1).build();
        FlowStatsEntry forwardTransitPoint2Updated = new FlowStatsEntry(
                1, cookieForwardSubflowUpdated1.getValue(), 130L, 270L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_4, Collections.singletonList(forwardTransitPoint2Updated)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardTransitPoint2Updated, cookieForwardSubflowUpdated1, SWITCH_ID_4,
                false, false, false, SUB_FLOW_ID_1);

        FlowStatsEntry forwardEgressPoint1Updated = new FlowStatsEntry(
                1, cookieForwardSubflowUpdated1.getValue(), 120L, 260L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_5, Collections.singletonList(forwardEgressPoint1Updated)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardEgressPoint1Updated, cookieForwardSubflowUpdated1, SWITCH_ID_5,
                false, true, false, SUB_FLOW_ID_1);
        FlowSegmentCookie cookieForwardSubflowUpdated2 = haFlowUpdated.getForwardPath()
                .getCookie().toBuilder().subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2).build();
        FlowStatsEntry forwardEgressPoint2Updated = new FlowStatsEntry(
                1, cookieForwardSubflowUpdated2.getValue(), 110L, 250L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_6, Collections.singletonList(forwardEgressPoint2Updated)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardEgressPoint2Updated, cookieForwardSubflowUpdated2, SWITCH_ID_6,
                false, true, false, SUB_FLOW_ID_2);
    }

    /**
     * 4 switch ha-flow flow-stats test.
     * ''''''''/-4--5
     * 1--2--3
     * '''''''\----6
     */
    @Test
    public void haFlowStatsTest() {
        HaFlow haFlow = createHaFlow1();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                160L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardIngress, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                true, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardTransit1 = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                150L, 290L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardTransit1)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardTransit1, haFlow.getForwardPath().getCookie(), SWITCH_ID_2,
                false, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                140L, 280L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardYPoint, haFlow.getForwardPath().getCookie(), SWITCH_ID_3,
                false, false, true, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardTransitPoint2 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                130L, 270L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_4, Collections.singletonList(forwardTransitPoint2)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardTransitPoint2, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_4,
                false, false, false, SUB_FLOW_ID_1);

        FlowStatsEntry forwardEgressPoint1 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                120L, 260L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_5, Collections.singletonList(forwardEgressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardEgressPoint1, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_5,
                false, true, false, SUB_FLOW_ID_1);

        FlowStatsEntry forwardEgressPoint2 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_2.getValue(),
                110L, 250L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_6, Collections.singletonList(forwardEgressPoint2)));
        validateHaFlowStats(HA_FLOW_ID_1, forwardEgressPoint2, COOKIE_FORWARD_SUBFLOW_2, SWITCH_ID_6,
                false, true, false, SUB_FLOW_ID_2);

        //reverse:
        FlowSegmentCookie cookieReverse = haFlow.getReversePath().getCookie();

        FlowStatsEntry reverseIngressPoint1 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                110L, 240L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_5, Collections.singletonList(reverseIngressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_1, reverseIngressPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_5,
                true, false, false, SUB_FLOW_ID_1);

        FlowStatsEntry reverseTransitPoint2 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                100L, 230L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_4, Collections.singletonList(reverseTransitPoint2)));
        validateHaFlowStats(HA_FLOW_ID_1, reverseTransitPoint2, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_4,
                false, false, false, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                90L, 220L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(reverseYPoint)));
        validateHaFlowStats(HA_FLOW_ID_1, reverseYPoint, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_3, false,
                false, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseTransitPoint1 = new FlowStatsEntry(1, cookieReverse.getValue(),
                80L, 210L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseTransitPoint1)));
        validateHaFlowStats(HA_FLOW_ID_1, reverseTransitPoint1, cookieReverse, SWITCH_ID_2, false,
                false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry reverseEgress = new FlowStatsEntry(1, cookieReverse.getValue(),
                70L, 200L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(reverseEgress)));
        validateHaFlowStats(HA_FLOW_ID_1, reverseEgress, cookieReverse, SWITCH_ID_1, false,
                true, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry reverseIngressPoint2 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_2.getValue(),
                60L, 190L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_6, Collections.singletonList(reverseIngressPoint2)));
        validateHaFlowStats(HA_FLOW_ID_1, reverseIngressPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_6,
                true, false, false, SUB_FLOW_ID_2);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_2.getValue(),
                50L, 180L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(reverseYPoint2)));
        validateHaFlowStats(HA_FLOW_ID_1, reverseYPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_3, false,
                false, true, SUB_FLOW_ID_2);
    }

    /**
     * 4 switch ha-flow meter-stats test.
     * ''''''''/-4--5
     * 1--2--3
     * '''''''\----6
     */
    @Test
    public void haFlowMeterStatsTest() {
        HaFlow haFlow = createHaFlow1();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        MeterStatsEntry forward = new MeterStatsEntry(METER_ID_HA_FLOW_PATH_FORWARD.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(forward)));
        validateHaFlowMeterStats(HA_FLOW_ID_1, forward, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                METER_ID_HA_FLOW_PATH_FORWARD.getValue(), SUB_FLOW_ID_SHARED, false, true);

        //reverse
        MeterStatsEntry reverse1 = new MeterStatsEntry(METER_ID_REVERSE_SUB_PATH_1.getValue(), 300L, 400L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_5, Collections.singletonList(reverse1)));
        validateHaFlowMeterStats(HA_FLOW_ID_1, reverse1, COOKIE_REVERSE_SUBFLOW_1,
                SWITCH_ID_5, METER_ID_REVERSE_SUB_PATH_1.getValue(), SUB_FLOW_ID_1, false, true);

        MeterStatsEntry reverse2 = new MeterStatsEntry(METER_ID_REVERSE_SUB_PATH_2.getValue(), 200L, 300L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_6, Collections.singletonList(reverse2)));
        validateHaFlowMeterStats(HA_FLOW_ID_1, reverse2, COOKIE_REVERSE_SUBFLOW_2,
                SWITCH_ID_6, METER_ID_REVERSE_SUB_PATH_2.getValue(), SUB_FLOW_ID_2, false, true);

        MeterStatsEntry reverseYPoint =
                new MeterStatsEntry(METER_ID_HA_FLOW_PATH_REVERSE.getValue(), 100L, 200L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_3, Collections.singletonList(reverseYPoint)));
        validateHaFlowMeterStats(HA_FLOW_ID_1, reverseYPoint, null,
                SWITCH_ID_3, METER_ID_HA_FLOW_PATH_REVERSE.getValue(),
                null, true, false);
    }

    /**
     * 4 switch ha-flow group-stats test.
     * ''''''''/-4--5
     * 1--2--3
     * '''''''\----6
     */
    @Test
    public void haFlowGroupStatsTest() {
        HaFlow haFlow = createHaFlow1();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        GroupStatsEntry forward = new GroupStatsEntry(GROUP_ID_1.intValue(), 400L, 500L);
        sendStatsMessage(new GroupStatsData(SWITCH_ID_3, Collections.singletonList(forward)));
        validateHaFlowGroupStats(HA_FLOW_ID_1, forward, GROUP_ID_1.intValue(), SWITCH_ID_3);
    }

    /**
     * 3 switch ha-flow flow-stats test.
     * ''/----3
     * 2
     * ''\----4
     */
    @Test
    public void haFlowStatsTest2() {
        HaFlow haFlow = createHaFlow2();
        sendUpdateHaFlowPathInfo(haFlow);

        //forward
        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                200L, 300L, 11, 12);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_2, forwardYPoint, haFlow.getForwardPath().getCookie(), SWITCH_ID_2, true,
                false, true, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardEgressPoint1 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                210L, 310L, 13, 14);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(forwardEgressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_2, forwardEgressPoint1, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_3, false,
                true, false, SUB_FLOW_ID_1);

        FlowStatsEntry forwardEgressPoint2 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_2.getValue(),
                220L, 320L, 15, 16);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_4, Collections.singletonList(forwardEgressPoint2)));
        validateHaFlowStats(HA_FLOW_ID_2, forwardEgressPoint2, COOKIE_FORWARD_SUBFLOW_2, SWITCH_ID_4, false,
                true, false, SUB_FLOW_ID_2);


        //reverse:
        FlowStatsEntry reverseIngressPoint1 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                250L, 600L, 10, 80);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(reverseIngressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_2, reverseIngressPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_3, true,
                false, false, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                350L, 500L, 20, 70);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint)));
        validateHaFlowStats(HA_FLOW_ID_2, reverseYPoint, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_2, false,
                true, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseIngressPoint2 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_2.getValue(),
                450L, 400L, 30, 60);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_4, Collections.singletonList(reverseIngressPoint2)));
        validateHaFlowStats(HA_FLOW_ID_2, reverseIngressPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_4, true,
                false, false, SUB_FLOW_ID_2);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_2.getValue(),
                550L, 300L, 40, 50);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint2)));
        validateHaFlowStats(HA_FLOW_ID_2, reverseYPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_2, false,
                true, true, SUB_FLOW_ID_2);
    }

    /**
     * 3 switch ha-flow meter-stats test.
     * ''/----3
     * 2
     * ''\----4
     */
    @Test
    public void haFlowMeterStatsTest2() {
        HaFlow haFlow = createHaFlow2();
        sendUpdateHaFlowPathInfo(haFlow);

        MeterStatsEntry forward = new MeterStatsEntry(METER_ID_HA_FLOW_PATH_FORWARD.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_2, Collections.singletonList(forward)));
        validateHaFlowMeterStats(HA_FLOW_ID_2, forward, COOKIE_FORWARD, SWITCH_ID_2,
                METER_ID_HA_FLOW_PATH_FORWARD.getValue(),
                SUB_FLOW_ID_SHARED, false, true); //since it is ingress, set yPoint flag false.

        //reverse
        MeterStatsEntry reverse1 = new MeterStatsEntry(METER_ID_REVERSE_SUB_PATH_1.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_3, Collections.singletonList(reverse1)));
        validateHaFlowMeterStats(HA_FLOW_ID_2, reverse1, COOKIE_REVERSE_SUBFLOW_1,
                SWITCH_ID_3, METER_ID_REVERSE_SUB_PATH_1.getValue(), SUB_FLOW_ID_1, false, true);

        MeterStatsEntry reverse2 = new MeterStatsEntry(METER_ID_REVERSE_SUB_PATH_2.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_4, Collections.singletonList(reverse2)));
        validateHaFlowMeterStats(HA_FLOW_ID_2, reverse2, COOKIE_REVERSE_SUBFLOW_2,
                SWITCH_ID_4, METER_ID_REVERSE_SUB_PATH_2.getValue(), SUB_FLOW_ID_2, false, true);

        MeterStatsEntry reverseYPoint = new MeterStatsEntry(METER_ID_HA_FLOW_PATH_REVERSE.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint)));
        validateHaFlowMeterStats(HA_FLOW_ID_2, reverseYPoint, null, SWITCH_ID_2,
                METER_ID_HA_FLOW_PATH_REVERSE.getValue(), null, true, false);
    }

    /**
     * 3 switch ha-flow group-stats test.
     * ''/----3
     * 2
     * ''\----4
     */
    @Test
    public void haFlowGroupStatsTest2() {
        HaFlow haFlow = createHaFlow2();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        GroupStatsEntry forward = new GroupStatsEntry(GROUP_ID_1.intValue(), 200L, 500L);
        sendStatsMessage(new GroupStatsData(SWITCH_ID_2, Collections.singletonList(forward)));
        validateHaFlowGroupStats(HA_FLOW_ID_2, forward, GROUP_ID_1.intValue(), SWITCH_ID_2);
    }

    /**
     * 3 switch ha-flow flow-stats test.
     * ''''''/----3  where 2 is an y-point switch
     * 1----2
     */
    @Test
    public void haFlowStatsTest3() {
        HaFlow haFlow = createHaFlow3();

        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateHaFlowStats(HA_FLOW_ID_3, forwardIngress, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                true, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_3, forwardYPoint, haFlow.getForwardPath().getCookie(), SWITCH_ID_2,
                false, true, true, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardEgressPoint1 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(forwardEgressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_3, forwardEgressPoint1, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_3,
                false, true, false, SUB_FLOW_ID_1);

        //reverse:
        FlowSegmentCookie cookieReverse = haFlow.getReversePath().getCookie();

        FlowStatsEntry reverseIngressPoint1 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(reverseIngressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_3, reverseIngressPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_3,
                true, false, false, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint1 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint1)));
        validateHaFlowStats(HA_FLOW_ID_3, reverseYPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_2,
                false, false, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseEgress = new FlowStatsEntry(1, cookieReverse.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(reverseEgress)));
        validateHaFlowStats(HA_FLOW_ID_3, reverseEgress, cookieReverse, SWITCH_ID_1,
                false, true, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_2.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint2)));
        validateHaFlowStats(HA_FLOW_ID_3, reverseYPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_2,
                true, false, true, SUB_FLOW_ID_2);
    }

    /**
     * 3 switch ha-flow meter-stats test.
     * ''''''/----3 where 2 is an y-point switch
     * 1----2
     */
    @Test
    public void haFlowMeterStatsTest3() {
        HaFlow haFlow = createHaFlow3();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        MeterStatsEntry forward = new MeterStatsEntry(METER_ID_HA_FLOW_PATH_FORWARD.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(forward)));
        validateHaFlowMeterStats(HA_FLOW_ID_3, forward, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                METER_ID_HA_FLOW_PATH_FORWARD.getValue(), SUB_FLOW_ID_SHARED, false, true);

        //reverse
        MeterStatsEntry reverse1 = new MeterStatsEntry(METER_ID_REVERSE_SUB_PATH_1.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_3, Collections.singletonList(reverse1)));
        validateHaFlowMeterStats(HA_FLOW_ID_3, reverse1, COOKIE_REVERSE_SUBFLOW_1,
                SWITCH_ID_3, METER_ID_REVERSE_SUB_PATH_1.getValue(), SUB_FLOW_ID_1, false, true);

        MeterStatsEntry reverseYPoint = new MeterStatsEntry(METER_ID_HA_FLOW_PATH_REVERSE.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint)));
        validateHaFlowMeterStats(HA_FLOW_ID_3, reverseYPoint, COOKIE_REVERSE_SUBFLOW_2,
                SWITCH_ID_2, METER_ID_HA_FLOW_PATH_REVERSE.getValue(), SUB_FLOW_ID_2, true, true);
    }

    /**
     * 2 switch ha-flow flow-stats test.
     * '''''''/
     * 1----2  (y-point 2) where 2 is an y-point switch
     * '''''''\
     */
    @Test
    public void haFlowStatsTest4() {
        HaFlow haFlow = createHaFlow4();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateHaFlowStats(HA_FLOW_ID_4, forwardIngress, haFlow.getForwardPath().getCookie(), SWITCH_ID_1, true,
                false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_4, forwardYPoint, haFlow.getForwardPath().getCookie(), SWITCH_ID_2, false,
                true, true, SUB_FLOW_ID_SHARED);


        //reverse:
        FlowSegmentCookie cookieReverse = haFlow.getReversePath().getCookie();

        FlowStatsEntry reverseYPoint1 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_1.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint1)));
        validateHaFlowStats(HA_FLOW_ID_4, reverseYPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_2, true,
                false, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_2.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint2)));
        validateHaFlowStats(HA_FLOW_ID_4, reverseYPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_2, true,
                false, true, SUB_FLOW_ID_2);

        FlowStatsEntry reverseEgress = new FlowStatsEntry(1, cookieReverse.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(reverseEgress)));
        validateHaFlowStats(HA_FLOW_ID_4, reverseEgress, cookieReverse, SWITCH_ID_1, false,
                true, false, SUB_FLOW_ID_SHARED);
    }

    /**
     * 2 switch ha-flow meter-stats test.
     * '''''''/
     * 1----2  (y-point 2) where 2 is an y-point switch
     * '''''''\
     */
    @Test
    public void haFlowMeterStatsTest4() {
        HaFlow haFlow = createHaFlow4();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        MeterStatsEntry forward = new MeterStatsEntry(
                METER_ID_HA_FLOW_PATH_FORWARD.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(forward)));
        validateHaFlowMeterStats(HA_FLOW_ID_4, forward, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                METER_ID_HA_FLOW_PATH_FORWARD.getValue(), SUB_FLOW_ID_SHARED, false, true);

        //reverse
        MeterStatsEntry reverse1 = new MeterStatsEntry(
                METER_ID_HA_FLOW_PATH_REVERSE.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_2, Collections.singletonList(reverse1)));
        validateHaFlowMeterStatsYPointIsBothIngress(HA_FLOW_ID_4, reverse1, COOKIE_REVERSE, COOKIE_REVERSE_SUBFLOW_1,
                COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_2, METER_ID_HA_FLOW_PATH_REVERSE.getValue(),
                SUB_FLOW_ID_1, SUB_FLOW_ID_2);

    }

    /**
     * 2 switch ha-flow flow-stats test.
     * 1-----2   where 1 is an y-point switch
     * ''\
     * forward: 1 is an y-point, 1 is an ingress, 1 is egress for one subflow, 2 is egress for another subflow
     * reverse: 1 is an y-point and egress for both subflows, 1 is an ingress for only one subflow,
     * 2 is an ingress for another subflow
     */
    @Test
    public void haFlowStatsTest5() {
        HaFlow haFlow = createHaFlow5();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, COOKIE_FORWARD.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_5, forwardYPoint, COOKIE_FORWARD, SWITCH_ID_1,
                true, true, true, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardEgress1 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardEgress1)));
        validateHaFlowStats(HA_FLOW_ID_5, forwardEgress1, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_2,
                false, true, false, SUB_FLOW_ID_1);


        //reverse:

        FlowStatsEntry reverseIngress = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_1.getValue(), 150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseIngress)));
        validateHaFlowStats(HA_FLOW_ID_5, reverseIngress, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_2, true,
                false, false, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint1 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_1.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(reverseYPoint1)));
        validateHaFlowStats(HA_FLOW_ID_5, reverseYPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_1, false,
                true, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_2.getValue(),
                150L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(reverseYPoint2)));
        validateHaFlowStats(HA_FLOW_ID_5, reverseYPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_1, true,
                true, true, SUB_FLOW_ID_2);
    }

    /**
     * 2 switch ha-flow meter-stats test.
     * 1-----2   where 1 is an y-point switch
     * ''\
     * forward: 1 is an y-point, 1 is an ingress, 1 is egress for one subflow, 2 is egress for another subflow
     * reverse: 1 is an y-point and egress for both subflows, 1 is an ingress for only one subflow,
     * 2 is an ingress for another subflow
     */
    @Test
    public void haFlowMeterStatsTest5() {
        HaFlow haFlow = createHaFlow5();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        MeterStatsEntry forward = new MeterStatsEntry(
                METER_ID_HA_FLOW_PATH_FORWARD.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(forward)));
        validateHaFlowMeterStats(HA_FLOW_ID_5, forward, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                METER_ID_HA_FLOW_PATH_FORWARD.getValue(), SUB_FLOW_ID_SHARED, false, true);

        //reverse
        MeterStatsEntry reverse1 = new MeterStatsEntry(
                METER_ID_REVERSE_SUB_PATH_1.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_2, Collections.singletonList(reverse1)));
        validateHaFlowMeterStats(HA_FLOW_ID_5, reverse1, COOKIE_REVERSE_SUBFLOW_1,
                SWITCH_ID_2, METER_ID_REVERSE_SUB_PATH_1.getValue(), SUB_FLOW_ID_1, false, true);

        MeterStatsEntry reverse2 = new MeterStatsEntry(
                METER_ID_HA_FLOW_PATH_REVERSE.getValue(), 400L, 500L);
        sendStatsMessage(new MeterStatsData(SWITCH_ID_1, Collections.singletonList(reverse2)));
        validateHaFlowMeterStats(HA_FLOW_ID_5, reverse2, COOKIE_REVERSE_SUBFLOW_2,
                SWITCH_ID_1, METER_ID_HA_FLOW_PATH_REVERSE.getValue(), SUB_FLOW_ID_2, true, true);
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
                    assertEquals(FLOW_ID, datapoint.getTags().get("flowid"));
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
                    assertEquals(FLOW_ID, datapoint.getTags().get("flowid"));
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

    private void validateHaFlowStats(String haFlowId,
                                     FlowStatsEntry flowStats, FlowSegmentCookie cookie, SwitchId switchId,
                                     boolean includeIngress, boolean includeEgress,
                                     boolean includeYPoint, String subFlowId) {
        int expectedDatapointCount = 3;
        if (includeIngress) {
            expectedDatapointCount += 3;
        }
        if (includeEgress) {
            expectedDatapointCount += 3;
        }
        if (includeYPoint) {
            expectedDatapointCount += 3;
        }

        List<Datapoint> datapoints = pollDatapoints(expectedDatapointCount);
        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(flowStats.getPacketCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.raw.packets").getValue().longValue());
        assertEquals(flowStats.getByteCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.raw.bytes").getValue().longValue());
        assertEquals(flowStats.getByteCount() * 8,
                datapointMap.get(METRIC_PREFIX + "haflow.raw.bits").getValue().longValue());

        if (includeYPoint) {
            assertEquals(flowStats.getPacketCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.ypoint.packets").getValue().longValue());
            assertEquals(flowStats.getByteCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.ypoint.bytes").getValue().longValue());
            assertEquals(flowStats.getByteCount() * 8,
                    datapointMap.get(METRIC_PREFIX + "haflow.ypoint.bits").getValue().longValue());
        }

        if (includeIngress) {
            assertEquals(flowStats.getPacketCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.ingress.packets").getValue().longValue());
            assertEquals(flowStats.getByteCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.ingress.bytes").getValue().longValue());
            assertEquals(flowStats.getByteCount() * 8,
                    datapointMap.get(METRIC_PREFIX + "haflow.ingress.bits").getValue().longValue());

        }
        if (includeEgress) {
            assertEquals(flowStats.getPacketCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.packets").getValue().longValue());
            assertEquals(flowStats.getByteCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.bytes").getValue().longValue());
            assertEquals(flowStats.getByteCount() * 8,
                    datapointMap.get(METRIC_PREFIX + "haflow.bits").getValue().longValue());
        }


        String direction = cookie.getDirection().name().toLowerCase();
        int expectedRawTagsCount = 8;
        int expectedEndpointTagsCount = 3;

        datapoints.forEach(datapoint -> {
            switch (datapoint.getMetric()) {
                case METRIC_PREFIX + "haflow.raw.packets":
                case METRIC_PREFIX + "haflow.raw.bytes":
                case METRIC_PREFIX + "haflow.raw.bits":
                    assertEquals(expectedRawTagsCount, datapoint.getTags().size());
                    assertEquals(haFlowId, datapoint.getTags().get("ha_flow_id"));
                    assertEquals(subFlowId, datapoint.getTags().get("flowid"));
                    assertEquals(direction, datapoint.getTags().get("direction"));
                    assertEquals(String.valueOf(flowStats.getTableId()), datapoint.getTags().get("tableid"));
                    assertEquals(Long.toString(cookie.getValue()), datapoint.getTags().get("cookie"));
                    assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
                    assertEquals(Integer.toString(flowStats.getOutPort()), datapoint.getTags().get("outPort"));
                    assertEquals(Integer.toString(flowStats.getInPort()), datapoint.getTags().get("inPort"));
                    break;
                case METRIC_PREFIX + "haflow.ingress.packets":
                case METRIC_PREFIX + "haflow.ingress.bytes":
                case METRIC_PREFIX + "haflow.ingress.bits":
                case METRIC_PREFIX + "haflow.ypoint.packets":
                case METRIC_PREFIX + "haflow.ypoint.bytes":
                case METRIC_PREFIX + "haflow.ypoint.bits":
                case METRIC_PREFIX + "haflow.packets":
                case METRIC_PREFIX + "haflow.bytes":
                case METRIC_PREFIX + "haflow.bits":
                    assertEquals(expectedEndpointTagsCount, datapoint.getTags().size());
                    assertEquals(subFlowId, datapoint.getTags().get("flowid"));
                    assertEquals(haFlowId, datapoint.getTags().get("ha_flow_id"));
                    assertEquals(direction, datapoint.getTags().get("direction"));
                    break;
                default:
                    throw new AssertionError(format("Unknown metric: %s", datapoint.getMetric()));
            }
        });
    }

    private void validateHaFlowMeterStats(String haFlowId, MeterStatsEntry meterStats, FlowSegmentCookie cookie,
                                          SwitchId switchId, Long meterId, String subFlowId,
                                          boolean isYPoint, boolean isIngress) {
        if (!isYPoint && !isIngress) {
            throw new IllegalArgumentException("isYpoint and isIngress can not be both false");
        }
        int expectedDatapointCount = 0;
        if (isYPoint) {
            expectedDatapointCount += 3;
        }
        if (isIngress) {
            expectedDatapointCount += 3;
        }

        List<Datapoint> datapoints = pollDatapoints(expectedDatapointCount);
        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        if (isYPoint) {
            assertEquals(meterStats.getByteInCount() * 8,
                    datapointMap.get(METRIC_PREFIX + "haflow.meter.ypoint.bits").getValue().longValue());
            assertEquals(meterStats.getByteInCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.meter.ypoint.bytes").getValue().longValue());
            assertEquals(meterStats.getPacketsInCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.meter.ypoint.packets").getValue().longValue());
        }

        if (isIngress) {
            assertEquals(meterStats.getByteInCount() * 8,
                    datapointMap.get(METRIC_PREFIX + "haflow.meter.bits").getValue().longValue());
            assertEquals(meterStats.getByteInCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.meter.bytes").getValue().longValue());
            assertEquals(meterStats.getPacketsInCount(),
                    datapointMap.get(METRIC_PREFIX + "haflow.meter.packets").getValue().longValue());
        }

        datapoints.forEach(datapoint -> {
            switch (datapoint.getMetric()) {
                case METRIC_PREFIX + "haflow.meter.ypoint.bits":
                case METRIC_PREFIX + "haflow.meter.ypoint.bytes":
                case METRIC_PREFIX + "haflow.meter.ypoint.packets":
                    assertEquals(3, datapoint.getTags().size());
                    assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
                    assertEquals(Long.toString(meterId), datapoint.getTags().get("meterid"));
                    assertEquals(haFlowId, datapoint.getTags().get("ha_flow_id"));
                    break;
                case METRIC_PREFIX + "haflow.meter.bits":
                case METRIC_PREFIX + "haflow.meter.bytes":
                case METRIC_PREFIX + "haflow.meter.packets":
                    assertEquals(6, datapoint.getTags().size());
                    assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
                    assertEquals(Long.toString(meterId), datapoint.getTags().get("meterid"));
                    assertEquals(haFlowId, datapoint.getTags().get("ha_flow_id"));
                    assertEquals(subFlowId, datapoint.getTags().get("flowid"));
                    String direction = cookie.getDirection().name().toLowerCase();
                    assertEquals(direction, datapoint.getTags().get("direction"));
                    assertEquals(Long.toString(cookie.getValue()), datapoint.getTags().get("cookie"));
                    break;
                default:
                    throw new AssertionError(format("Unknown metric: %s", datapoint.getMetric()));
            }
        });
    }

    private void validateHaFlowMeterStatsYPointIsBothIngress(String haFlowId,
                                                             MeterStatsEntry meterStats, FlowSegmentCookie cookie,
                                                             FlowSegmentCookie cookieSubFlow1,
                                                             FlowSegmentCookie cookieSubFlow2,
                                                             SwitchId switchId, Long meterId,
                                                             String subFlowId1, String subFlowId2) {
        int expectedDatapointCount = 9;
        List<Datapoint> datapoints = pollDatapoints(expectedDatapointCount);

        Map<String, List<Datapoint>> datapointMap = createDatapointMapOfLists(datapoints);

        assertEquals(meterStats.getByteInCount() * 8,
                datapointMap.get(METRIC_PREFIX + "haflow.meter.ypoint.bits").get(0).getValue().longValue());
        assertEquals(meterStats.getByteInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.meter.ypoint.bytes").get(0).getValue().longValue());
        assertEquals(meterStats.getPacketsInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.meter.ypoint.packets").get(0).getValue().longValue());

        assertEquals(meterStats.getByteInCount() * 8,
                datapointMap.get(METRIC_PREFIX + "haflow.meter.bits").get(0).getValue().longValue());
        assertEquals(meterStats.getByteInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.meter.bytes").get(0).getValue().longValue());
        assertEquals(meterStats.getPacketsInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.meter.packets").get(0).getValue().longValue());
        assertEquals(meterStats.getByteInCount() * 8,
                datapointMap.get(METRIC_PREFIX + "haflow.meter.bits").get(1).getValue().longValue());
        assertEquals(meterStats.getByteInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.meter.bytes").get(1).getValue().longValue());
        assertEquals(meterStats.getPacketsInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.meter.packets").get(1).getValue().longValue());


        String direction = cookie.getDirection().name().toLowerCase();
        Map<String, List<Datapoint>> datapointMapOfLists = createDatapointMapOfLists(datapoints);
        datapointMapOfLists.keySet().forEach(metricName -> {
            switch (metricName) {
                case METRIC_PREFIX + "haflow.meter.ypoint.bits":
                case METRIC_PREFIX + "haflow.meter.ypoint.bytes":
                case METRIC_PREFIX + "haflow.meter.ypoint.packets":
                    Datapoint datapoint = datapointMapOfLists.get(metricName).get(0);
                    assertDataPoint(haFlowId, datapoint, false, 3, switchId, meterId,
                            null, null, null);
                    assertDataPoint(haFlowId, datapoint, false, 3, switchId, meterId,
                            null, null, null);
                    assertDataPoint(haFlowId, datapoint, false, 3, switchId, meterId,
                            null, null, null);
                    break;
                case METRIC_PREFIX + "haflow.meter.bits":
                case METRIC_PREFIX + "haflow.meter.bytes":
                case METRIC_PREFIX + "haflow.meter.packets":
                    Map<String, Datapoint> datapointByFlowId = datapointMapOfLists.get(metricName).stream()
                            .collect(Collectors.toMap((key -> key.getTags().get("flowid")), (Function.identity())));
                    Map<String, FlowSegmentCookie> cookieMap = new HashMap<>();
                    cookieMap.put(subFlowId1, cookieSubFlow1);
                    cookieMap.put(subFlowId2, cookieSubFlow2);

                    for (String subflowId : new String[]{subFlowId1, subFlowId2}) {
                        Datapoint currDatapoint = datapointByFlowId.get(subflowId);
                        assertEquals(6, currDatapoint.getTags().size());
                        assertEquals(switchId.toOtsdFormat(), currDatapoint.getTags().get("switchid"));
                        assertEquals(Long.toString(meterId), currDatapoint.getTags().get("meterid"));
                        assertEquals(haFlowId, currDatapoint.getTags().get("ha_flow_id"));
                        assertEquals(subflowId, currDatapoint.getTags().get("flowid"));
                        assertEquals(direction, currDatapoint.getTags().get("direction"));
                        assertEquals(Long.toString(cookieMap.get(subflowId).getValue()),
                                currDatapoint.getTags().get("cookie"));
                    }
                    break;
                default:
                    throw new AssertionError(format("Unknown metric: %s", metricName));

            }
        });
    }

    private void assertDataPoint(String haFlowId, Datapoint datapoint, boolean isIngress,
                                 int expectedRawTagsCount, SwitchId switchId,
                                 Long meterId, Set<String> subFlowIds, String direction, Set<String> cookies) {
        assertEquals(expectedRawTagsCount, datapoint.getTags().size());
        assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
        assertEquals(Long.toString(meterId), datapoint.getTags().get("meterid"));
        assertEquals(haFlowId, datapoint.getTags().get("ha_flow_id"));
        if (isIngress) {
            assertTrue(subFlowIds.contains(datapoint.getTags().get("flowid")));
            assertEquals(direction, datapoint.getTags().get("direction"));
            assertTrue(cookies.contains(datapoint.getTags().get("cookie")));
        }
    }

    private void validateHaFlowGroupStats(String haFlowId, GroupStatsEntry groupStats, int groupId, SwitchId switchId) {
        int expectedDatapointCount = 3;

        List<Datapoint> datapoints = pollDatapoints(expectedDatapointCount);
        Map<String, Datapoint> datapointMap = createDatapointMap(datapoints);

        assertEquals(groupStats.getByteInCount() * 8,
                datapointMap.get(METRIC_PREFIX + "haflow.group.ypoint.bits").getValue().longValue());
        assertEquals(groupStats.getByteInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.group.ypoint.bytes").getValue().longValue());
        assertEquals(groupStats.getPacketsInCount(),
                datapointMap.get(METRIC_PREFIX + "haflow.group.ypoint.packets").getValue().longValue());

        int expectedRawTagsCount = 3;
        datapoints.forEach(datapoint -> {
            switch (datapoint.getMetric()) {
                case METRIC_PREFIX + "haflow.group.ypoint.bits":
                case METRIC_PREFIX + "haflow.group.ypoint.bytes":
                case METRIC_PREFIX + "haflow.group.ypoint.packets":
                    assertEquals(expectedRawTagsCount, datapoint.getTags().size());
                    assertEquals(switchId.toOtsdFormat(), datapoint.getTags().get("switchid"));
                    assertEquals(Integer.toString(groupId), datapoint.getTags().get("groupid"));
                    assertEquals(haFlowId, datapoint.getTags().get("ha_flow_id"));
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
                    assertEquals(FLOW_ID, datapoint.getTags().get("flowid"));
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

    private Flow createOneSwitchFlow(SwitchId switchId) {
        Switch sw = createSwitch(switchId);

        Flow flow = new TestFlowBuilder(FLOW_ID)
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

        Flow flow = new TestFlowBuilder(FLOW_ID)
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

    /* This method will create the ha-flow with the following structure.
     *        /-4--5
     *1--2--3
     *        \----6
     * @return HaFlow.class
     */
    private HaFlow createHaFlow1() {
        Switch switch1 = createSwitch(SWITCH_ID_1); //shared,
        Switch switch2 = createSwitch(SWITCH_ID_2); //transit,
        Switch switch3 = createSwitch(SWITCH_ID_3); //y-point
        Switch switch4 = createSwitch(SWITCH_ID_4); //transit
        Switch switch5 = createSwitch(SWITCH_ID_5); // endpoint
        Switch switch6 = createSwitch(SWITCH_ID_6); // endpoint

        HaFlow haFlow = buildHaFlow(HA_FLOW_ID_1, switch1, PORT_1, VLAN_1,
                INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1,
                DESCRIPTION_1, PathComputationStrategy.COST, FlowStatus.UP,
                true, true, true,
                true, true);

        haFlowRepository.add(haFlow);
        // enough to provide only endpoint
        HaSubFlow subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, switch5, PORT_1, VLAN_1, ZERO_INNER_VLAN, DESCRIPTION_1);
        haSubFlowRepository.add(subFlow1);

        HaSubFlow subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, switch6, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haSubFlowRepository.add(subFlow2);
        haFlow.setHaSubFlows(newHashSet(subFlow1, subFlow2));

        //  provide only shared switch, remove meter y-point, since it valid only for reverse direction.
        HaFlowPath haPath1 = buildHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_FORWARD,
                METER_ID_HA_FLOW_PATH_FORWARD, null, switch1,
                SWITCH_ID_3, GROUP_ID_1);
        // keep meters only for y-point, reverse
        HaFlowPath haPath2 = buildHaFlowPath(PATH_ID_2, BANDWIDTH_1, COOKIE_REVERSE,
                null, METER_ID_HA_FLOW_PATH_REVERSE, switch1,
                SWITCH_ID_3, null); // there is no group here, as reverse

        haFlowPathRepository.add(haPath1);
        haFlowPathRepository.add(haPath2);

        FlowPath flowPathForward1 = createPathWithSegments(SUB_PATH_ID_1, haPath1, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, null, switch1, switch2, switch3, switch4, switch5);
        FlowPath flowPathForward2 = createPathWithSegments(SUB_PATH_ID_2, haPath1, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, null, switch1, switch2, switch3, switch6);
        haPath1.setSubPaths(Lists.newArrayList(flowPathForward1, flowPathForward2));
        haPath1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));

        FlowPath flowPathReverse1 = createPathWithSegments(SUB_PATH_ID_3, haPath2, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, METER_ID_REVERSE_SUB_PATH_1,
                switch5, switch4, switch3, switch2, switch1);
        FlowPath flowPathReverse2 = createPathWithSegments(SUB_PATH_ID_4, haPath2, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2,
                METER_ID_REVERSE_SUB_PATH_2, switch6, switch3, switch2, switch1);
        haPath2.setSubPaths(Lists.newArrayList(flowPathReverse1, flowPathReverse2));
        haPath2.setHaSubFlows(Lists.newArrayList(subFlow2, subFlow1));

        haFlow.addPaths(haPath1, haPath2);
        haFlow.setForwardPath(haPath1);
        haFlow.setReversePath(haPath2);
        return haFlow;
    }

    /* This method will create the ha-flow with the following structure:
     *  /----3
     * 2
     *  \----4
     * @return HaFlow.class
     */
    private HaFlow createHaFlow2() {
        Switch switch2 = createSwitch(SWITCH_ID_2); //y-point
        Switch switch3 = createSwitch(SWITCH_ID_3); // endpoint
        Switch switch4 = createSwitch(SWITCH_ID_4); // endpoint

        HaFlow haFlow = buildHaFlow(HA_FLOW_ID_2, switch2, PORT_1, VLAN_1,
                INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1,
                DESCRIPTION_1, PathComputationStrategy.COST, FlowStatus.UP,
                true, true, true,
                true, true);

        haFlowRepository.add(haFlow);
        // enough to provide only endpoint
        HaSubFlow subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, switch3, PORT_1, VLAN_1, ZERO_INNER_VLAN, DESCRIPTION_1);
        haSubFlowRepository.add(subFlow1);

        HaSubFlow subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, switch4, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haSubFlowRepository.add(subFlow2);
        haFlow.setHaSubFlows(newHashSet(subFlow1, subFlow2));

        //  provide only shared switch, remove meter y-point, since it valid only for reverse direction.
        HaFlowPath haPath1 = buildHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_FORWARD,
                METER_ID_HA_FLOW_PATH_FORWARD, null, switch2,
                SWITCH_ID_2, GROUP_ID_1);
        // keep meters only for y-point, reverse
        HaFlowPath haPath2 = buildHaFlowPath(PATH_ID_2, BANDWIDTH_1, COOKIE_REVERSE,
                null, METER_ID_HA_FLOW_PATH_REVERSE, switch2,
                SWITCH_ID_2, null); // there is no group here, as reverse

        haFlowPathRepository.add(haPath1);
        haFlowPathRepository.add(haPath2);

        FlowPath flowPathForward1 = create2SwitchPath(SUB_PATH_ID_1, haPath1, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, switch2, switch3, null);
        FlowPath flowPathForward2 = create2SwitchPath(SUB_PATH_ID_2, haPath1, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, switch2, switch4, null);
        haPath1.setSubPaths(Lists.newArrayList(flowPathForward1, flowPathForward2));
        haPath1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));

        FlowPath flowPathReverse1 = create2SwitchPath(SUB_PATH_ID_3, haPath2, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, switch3, switch2, METER_ID_REVERSE_SUB_PATH_1);
        FlowPath flowPathReverse2 = create2SwitchPath(SUB_PATH_ID_4, haPath2, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, switch4, switch2, METER_ID_REVERSE_SUB_PATH_2);
        haPath2.setSubPaths(Lists.newArrayList(flowPathReverse1, flowPathReverse2));
        haPath2.setHaSubFlows(Lists.newArrayList(subFlow2, subFlow1));

        haFlow.addPaths(haPath1, haPath2);
        haFlow.setForwardPath(haPath1);
        haFlow.setReversePath(haPath2);
        return haFlow;
    }


    /* This method will create the ha-flow with the following structure:
     *       /----3
     * 1----2
     * @return HaFlow.class
     */
    private HaFlow createHaFlow3() {
        Switch switch1 = createSwitch(SWITCH_ID_1); //shared,
        Switch switch2 = createSwitch(SWITCH_ID_2); //y-point
        Switch switch3 = createSwitch(SWITCH_ID_3); // endpoint

        HaFlow haFlow = buildHaFlow(HA_FLOW_ID_3, switch1, PORT_1, VLAN_1,
                INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1,
                DESCRIPTION_1, PathComputationStrategy.COST, FlowStatus.UP,
                true, true, true,
                true, true);

        haFlowRepository.add(haFlow);
        // enough to provide only endpoint
        HaSubFlow subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, switch3, PORT_1, VLAN_1, ZERO_INNER_VLAN, DESCRIPTION_1);
        haSubFlowRepository.add(subFlow1);

        HaSubFlow subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, switch2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haSubFlowRepository.add(subFlow2);
        haFlow.setHaSubFlows(newHashSet(subFlow1, subFlow2));

        //  provide only shared switch, remove meter y-point, since it valid only for reverse direction.
        HaFlowPath haPath1 = buildHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_FORWARD,
                METER_ID_HA_FLOW_PATH_FORWARD, null, switch1,
                SWITCH_ID_2, GROUP_ID_1);
        // keep meters only for y-point, reverse
        HaFlowPath haPath2 = buildHaFlowPath(PATH_ID_2, BANDWIDTH_1, COOKIE_REVERSE,
                null, METER_ID_HA_FLOW_PATH_REVERSE, switch1,
                SWITCH_ID_2, null); // there is no group here, as reverse

        haFlowPathRepository.add(haPath1);
        haFlowPathRepository.add(haPath2);

        FlowPath flowPathForward1 = createPathWithSegments(SUB_PATH_ID_1, haPath1, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, null, switch1, switch2, switch3);
        FlowPath flowPathForward2 = create2SwitchPath(SUB_PATH_ID_2, haPath1, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, switch1, switch2, null);
        haPath1.setSubPaths(Lists.newArrayList(flowPathForward1, flowPathForward2));
        haPath1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));

        FlowPath flowPathReverse1 = createPathWithSegments(SUB_PATH_ID_3, haPath2, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, METER_ID_REVERSE_SUB_PATH_1, switch3, switch2, switch1);
        FlowPath flowPathReverse2 = create2SwitchPath(SUB_PATH_ID_4, haPath2, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, switch2, switch1, METER_ID_REVERSE_SUB_PATH_2);
        haPath2.setSubPaths(Lists.newArrayList(flowPathReverse1, flowPathReverse2));
        haPath2.setHaSubFlows(Lists.newArrayList(subFlow2, subFlow1));

        haFlow.addPaths(haPath1, haPath2);
        haFlow.setForwardPath(haPath1);
        haFlow.setReversePath(haPath2);
        return haFlow;
    }

    /* This method will create the ha-flow with the following structure.
     *   1----2, where 2 is an y-point switch and 1 is an ingress for forward direction
     * @return HaFlow.class
     */
    private HaFlow createHaFlow4() {
        Switch switch1 = createSwitch(SWITCH_ID_1); //shared,
        Switch switch2 = createSwitch(SWITCH_ID_2); //y-point

        HaFlow haFlow = buildHaFlow(HA_FLOW_ID_4, switch1, PORT_1, VLAN_1,
                INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1,
                DESCRIPTION_1, PathComputationStrategy.COST, FlowStatus.UP,
                true, true, true,
                true, true);

        haFlowRepository.add(haFlow);
        // enough to provide only endpoint
        HaSubFlow subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, switch2, PORT_1, VLAN_1, ZERO_INNER_VLAN, DESCRIPTION_1);
        haSubFlowRepository.add(subFlow1);

        HaSubFlow subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, switch2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haSubFlowRepository.add(subFlow2);
        haFlow.setHaSubFlows(newHashSet(subFlow1, subFlow2));

        //  provide only shared switch, remove meter y-point, since it valid only for reverse direction.
        HaFlowPath haPath1 = buildHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_FORWARD,
                METER_ID_HA_FLOW_PATH_FORWARD, null, switch1,
                SWITCH_ID_2, GROUP_ID_1);
        // keep meters only for y-point, reverse
        HaFlowPath haPath2 = buildHaFlowPath(PATH_ID_2, BANDWIDTH_1, COOKIE_REVERSE,
                null, METER_ID_HA_FLOW_PATH_REVERSE, switch1,
                SWITCH_ID_2, null); // there is no group here, as reverse

        haFlowPathRepository.add(haPath1);
        haFlowPathRepository.add(haPath2);

        FlowPath flowPathForward1 = create2SwitchPath(SUB_PATH_ID_1, haPath1, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, switch1, switch2, null);
        FlowPath flowPathForward2 = create2SwitchPath(SUB_PATH_ID_2, haPath1, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, switch1, switch2, null);
        haPath1.setSubPaths(Lists.newArrayList(flowPathForward1, flowPathForward2));
        haPath1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));

        FlowPath flowPathReverse1 = create2SwitchPath(SUB_PATH_ID_3, haPath2, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, switch2, switch1, METER_ID_REVERSE_SUB_PATH_1);
        FlowPath flowPathReverse2 = create2SwitchPath(SUB_PATH_ID_4, haPath2, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, switch2, switch1, METER_ID_REVERSE_SUB_PATH_2);
        haPath2.setSubPaths(Lists.newArrayList(flowPathReverse1, flowPathReverse2));
        haPath2.setHaSubFlows(Lists.newArrayList(subFlow2, subFlow1));

        haFlow.addPaths(haPath1, haPath2);
        haFlow.setForwardPath(haPath1);
        haFlow.setReversePath(haPath2);
        return haFlow;
    }

    /* This method will create the ha-flow with the following structure.
     *   1----2, where 1 is an y-point, and 2 is an egress for forward direction
     * @return HaFlow.class
     */
    private HaFlow createHaFlow5() {
        Switch switch1 = createSwitch(SWITCH_ID_1); //y-point,
        Switch switch2 = createSwitch(SWITCH_ID_2);

        HaFlow haFlow = buildHaFlow(HA_FLOW_ID_5, switch1, PORT_1, VLAN_1,
                INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1,
                DESCRIPTION_1, PathComputationStrategy.COST, FlowStatus.UP,
                true, true, true,
                true, true);

        haFlowRepository.add(haFlow);
        // enough to provide only endpoint
        HaSubFlow subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, switch2, PORT_1, VLAN_1, ZERO_INNER_VLAN, DESCRIPTION_1);
        haSubFlowRepository.add(subFlow1);

        HaSubFlow subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, switch1, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haSubFlowRepository.add(subFlow2);
        haFlow.setHaSubFlows(newHashSet(subFlow1, subFlow2));

        //  provide only shared switch, remove meter y-point, since it valid only for reverse direction.
        HaFlowPath haPath1 = buildHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_FORWARD,
                METER_ID_HA_FLOW_PATH_FORWARD, null, switch1,
                SWITCH_ID_1, GROUP_ID_1);
        // keep meters only for y-point, reverse
        HaFlowPath haPath2 = buildHaFlowPath(PATH_ID_2, BANDWIDTH_1, COOKIE_REVERSE,
                null, METER_ID_HA_FLOW_PATH_REVERSE, switch1,
                SWITCH_ID_1, null); // there is no group here, as reverse

        haFlowPathRepository.add(haPath1);
        haFlowPathRepository.add(haPath2);

        FlowPath flowPathForward1 = create2SwitchPath(SUB_PATH_ID_1, haPath1, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, switch1, switch2, null);
        FlowPath flowPathForward2 = createPathWithSegments(SUB_PATH_ID_2, haPath1, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, null, switch1);
        haPath1.setSubPaths(Lists.newArrayList(flowPathForward1, flowPathForward2));
        haPath1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));

        FlowPath flowPathReverse1 = create2SwitchPath(SUB_PATH_ID_3, haPath2, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, switch2, switch1, METER_ID_REVERSE_SUB_PATH_1);
        FlowPath flowPathReverse2 = createPathWithSegments(SUB_PATH_ID_4, haPath2, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, METER_ID_REVERSE_SUB_PATH_2, switch1);
        haPath2.setSubPaths(Lists.newArrayList(flowPathReverse1, flowPathReverse2));
        haPath2.setHaSubFlows(Lists.newArrayList(subFlow2, subFlow1));

        haFlow.addPaths(haPath1, haPath2);
        haFlow.setForwardPath(haPath1);
        haFlow.setReversePath(haPath2);
        return haFlow;
    }

    private FlowPath createPathWithSegments(
            PathId pathId, HaFlowPath haFlowPath, HaSubFlow haSubFlow, FlowSegmentCookie.FlowSubType cookieSubType,
            MeterId meterId, Switch... switches) {
        FlowPath path = buildPath(pathId, haFlowPath, switches[0], switches[switches.length - 1]);
        path.setMeterId(meterId);
        path.setCookie(haFlowPath.getCookie().toBuilder().subType(cookieSubType).build());
        flowPathRepository.add(path);
        path.setSegments(buildSegments(path.getPathId(), switches));
        path.setHaSubFlow(haSubFlow);
        return path;
    }

    private FlowPath buildPathWithSegments(
            PathId pathId, HaFlowPath haFlowPath, HaSubFlow haSubFlow, FlowSegmentCookie.FlowSubType cookieSubType,
            MeterId meterId, Switch... switches) {
        FlowPath path = buildPath(pathId, haFlowPath, switches[0], switches[switches.length - 1]);
        path.setMeterId(meterId);
        path.setCookie(haFlowPath.getCookie().toBuilder().subType(cookieSubType).build());
        path.setSegments(buildSegments(path.getPathId(), switches));
        path.setHaSubFlow(haSubFlow);
        return path;
    }

    private FlowPath create2SwitchPath(
            PathId pathId, HaFlowPath haFlowPath, HaSubFlow haSubFlow, FlowSegmentCookie.FlowSubType cookieSubType,
            Switch switch1, Switch switch2, MeterId meterId) {
        FlowPath path = buildPath(pathId, haFlowPath, switch1, switch2);
        path.setMeterId(meterId);
        path.setCookie(haFlowPath.getCookie().toBuilder().subType(cookieSubType).build());
        flowPathRepository.add(path);
        PathSegment segment = PathSegment.builder()
                .pathId(pathId).srcSwitch(switch1).destSwitch(switch2).build();
        path.setSegments(Lists.newArrayList(segment));
        path.setHaSubFlow(haSubFlow);
        return path;
    }

    private Flow createFlowWithProtectedPath() {
        Switch srcSw = createSwitch(SWITCH_ID_1);
        Switch transitSw = createSwitch(SWITCH_ID_2);
        Switch dstSw = createSwitch(SWITCH_ID_3);

        Flow flow = new TestFlowBuilder(FLOW_ID)
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
        return new TestFlowBuilder(FLOW_ID)
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


    private void sendRemoveHaFlowPathInfo(FlowPath flowPath, HaFlow haFlow) {
        RemoveHaFlowPathInfo removeHaFlowPathInfo = new RemoveHaFlowPathInfo(
                flowPath.getHaFlowId(),
                flowPath.getCookie(),
                flowPath.isForward() ? flowPath.getHaFlowPath().getSharedPointMeterId()
                        : flowPath.getMeterId(),
                FlowPathMapper.INSTANCE.mapToPathNodes(haFlow, flowPath),
                false,
                false,
                flowPath.getCookie().getDirection() == FlowPathDirection.FORWARD
                        ? flowPath.getHaFlowPath().getYPointGroupId() : null,
                flowPath.getCookie().getDirection() == FlowPathDirection.REVERSE
                        ? flowPath.getHaFlowPath().getYPointMeterId() : null,
                flowPath.getHaSubFlowId(),
                flowPath.getHaFlowPath().getYPointSwitchId(),
                flowPath.getHaFlowPath().getSharedSwitchId()
        );
        sendNotification(removeHaFlowPathInfo);
    }

    private void sendRemoveHaFlowPathInfo(HaFlow haFlow) {
        haFlow.getPaths().forEach(haPath -> haPath.getSubPaths().forEach(subPath -> {
            RemoveHaFlowPathInfo removeHaFlowPathInfo = new RemoveHaFlowPathInfo(
                    subPath.getHaFlowId(),
                    subPath.getCookie(),
                    subPath.isForward() ? haPath.getSharedPointMeterId() : subPath.getMeterId(),
                    FlowPathMapper.INSTANCE.mapToPathNodes(haFlow, subPath),
                    false,
                    false,
                    subPath.getCookie().getDirection() == FlowPathDirection.FORWARD
                            ? haPath.getYPointGroupId() : null,
                    subPath.getCookie().getDirection() == FlowPathDirection.REVERSE
                            ? haPath.getYPointMeterId() : null,
                    subPath.getHaSubFlowId(),
                    haPath.getYPointSwitchId(),
                    haPath.getSharedSwitchId()
            );
            sendNotification(removeHaFlowPathInfo);
        }));
    }

    private void sendRemoveFlowPathInfo(FlowPath flowPath, Set<Integer> vlanStatistics, String yFlowId,
                                        SwitchId yPointSwitchId) {
        sendRemoveFlowPathInfo(flowPath, vlanStatistics, flowPath.hasIngressMirror(),
                flowPath.hasEgressMirror(), yFlowId, yPointSwitchId);
    }

    private void sendRemoveFlowPathInfo(
            FlowPath flowPath, Set<Integer> vlanStatistics, boolean ingressMirror,
            boolean egressMirror, String yFlowId, SwitchId yPointSwitchId) {
        RemoveFlowPathInfo pathInfo = new RemoveFlowPathInfo(
                flowPath.getFlowId(), yFlowId, yPointSwitchId, flowPath.getCookie(), flowPath.getMeterId(),
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

    private void sendUpdateHaFlowPathInfo(HaFlow haFlow) {
        haFlow.getPaths().forEach(haPath -> haPath.getSubPaths().forEach(subPath -> {
            UpdateHaFlowPathInfo haFlowPathInfo = new UpdateHaFlowPathInfo(
                    subPath.getHaFlowId(),
                    subPath.getCookie(),
                    subPath.isForward() ? haPath.getSharedPointMeterId() : subPath.getMeterId(),
                    FlowPathMapper.INSTANCE.mapToPathNodes(haFlow, subPath),
                    false,
                    false,
                    subPath.getCookie().getDirection() == FlowPathDirection.FORWARD
                            ? haPath.getYPointGroupId() : null,
                    subPath.getCookie().getDirection() == FlowPathDirection.REVERSE
                            ? haPath.getYPointMeterId() : null,
                    subPath.getHaSubFlowId(),
                    haPath.getYPointSwitchId(),
                    haPath.getSharedSwitchId()
            );
            sendNotification(haFlowPathInfo);
        }));
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
        InfoMessage infoMessage = new InfoMessage(notification, timestamp, UUID.randomUUID().toString(),
                null, null);
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

    private Map<String, List<Datapoint>> createDatapointMapOfLists(List<Datapoint> datapoints) {
        return datapoints
                .stream()
                .collect(Collectors.groupingBy(Datapoint::getMetric));
    }

    /**
     * 4 switch ha-flow flow-stats update test.
     * ''''''''/-4--5
     * 1--2--3
     * '''''''\----6
     */
    private HaFlow buildHaFlowWithoutPersistence(Switch switch1, Switch switch2, Switch switch3,
                                                 Switch switch4, Switch switch5, Switch switch6,
                                                 long flowEffectiveIdForward, long flowEffectiveIdReverse,
                                                 String forwardPathIdSuffix, String reversePathIdSuffix) {
        HaFlow haFlow = buildHaFlow(HA_FLOW_ID_1, switch1, PORT_1, VLAN_1,
                INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1,
                DESCRIPTION_1, PathComputationStrategy.COST, FlowStatus.UP,
                true, true, true,
                true, true);


        // enough to provide only endpoint
        HaSubFlow subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, switch5, PORT_1, VLAN_1, ZERO_INNER_VLAN, DESCRIPTION_1);


        HaSubFlow subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, switch6, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);

        haFlow.setHaSubFlows(newHashSet(subFlow1, subFlow2));

        //  provide only shared switch, remove meter y-point, since it valid only for reverse direction.
        HaFlowPath haPath1 = buildHaFlowPath(PATH_ID_1.append(forwardPathIdSuffix), BANDWIDTH_1,
                COOKIE_FORWARD.toBuilder().flowEffectiveId(flowEffectiveIdForward).build(),
                METER_ID_HA_FLOW_PATH_FORWARD, null, switch1,
                SWITCH_ID_3, GROUP_ID_1);
        // keep meters only for y-point, reverse
        HaFlowPath haPath2 = buildHaFlowPath(PATH_ID_2.append(reversePathIdSuffix), BANDWIDTH_1,
                COOKIE_REVERSE.toBuilder().flowEffectiveId(flowEffectiveIdReverse).build(),
                null, METER_ID_HA_FLOW_PATH_REVERSE, switch1,
                SWITCH_ID_3, null); // there is no group here, as reverse


        FlowPath flowPathForward1 = buildPathWithSegments(SUB_PATH_ID_1, haPath1, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, null, switch1, switch2, switch3, switch4, switch5);
        FlowPath flowPathForward2 = buildPathWithSegments(SUB_PATH_ID_2, haPath1, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2, null, switch1, switch2, switch3, switch6);
        haPath1.setSubPaths(Lists.newArrayList(flowPathForward1, flowPathForward2));
        haPath1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));

        FlowPath flowPathReverse1 = buildPathWithSegments(SUB_PATH_ID_3, haPath2, subFlow1,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1, METER_ID_REVERSE_SUB_PATH_1,
                switch5, switch4, switch3, switch2, switch1);
        FlowPath flowPathReverse2 = buildPathWithSegments(SUB_PATH_ID_4, haPath2, subFlow2,
                FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2,
                METER_ID_REVERSE_SUB_PATH_2, switch6, switch3, switch2, switch1);
        haPath2.setSubPaths(Lists.newArrayList(flowPathReverse1, flowPathReverse2));
        haPath2.setHaSubFlows(Lists.newArrayList(subFlow2, subFlow1));

        haFlow.addPaths(haPath1, haPath2);
        haFlow.setForwardPath(haPath1);
        haFlow.setReversePath(haPath2);
        return haFlow;
    }
}
