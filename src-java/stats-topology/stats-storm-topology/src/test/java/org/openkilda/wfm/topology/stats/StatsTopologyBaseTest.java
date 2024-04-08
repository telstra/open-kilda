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
import static org.apache.storm.utils.Utils.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.openkilda.model.FlowPathDirection.FORWARD;
import static org.openkilda.model.FlowPathDirection.REVERSE;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.StatsNotification;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
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
import org.openkilda.wfm.topology.TestKafkaConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Disabled("Disabled due to flaky behaviour. See https://github.com/telstra/open-kilda/issues/5563")
public class StatsTopologyBaseTest extends AbstractStormTest {

    protected static final long timestamp = System.currentTimeMillis();
    protected static final int POLL_TIMEOUT = 100;
    protected static final String POLL_DATAPOINT_ASSERT_MESSAGE = "Could not poll all %d datapoints, "
            + "got only %d records";
    protected static final String METRIC_PREFIX = "kilda.";
    protected static final int ENCAPSULATION_ID = 123;
    protected static final ObjectMapper objectMapper = new ObjectMapper();
    protected static final String COMPONENT_NAME = "stats";
    protected static final String RUN_ID = "blue";
    protected static final String ROOT_NODE = "kilda";
    protected static final int STAT_VLAN_1 = 4;
    protected static final int VLAN_1 = 5;
    protected static final int VLAN_2 = 6;
    protected static final int INNER_VLAN_1 = 8;
    protected static final int STAT_VLAN_2 = 5;
    protected static final int ZERO_INNER_VLAN = 0;
    protected static final int INNER_VLAN_2 = 9;

    protected static final HashSet<Integer> STAT_VLANS = Sets.newHashSet(STAT_VLAN_1, STAT_VLAN_2);
    protected static final int PRIORITY_1 = 13;
    protected static final String DESCRIPTION_1 = "description_1";
    protected static final String DESCRIPTION_2 = "description_2";
    protected static final long LATENCY_1 = 1;
    protected static final long LATENCY_2 = 2;
    protected static final int BANDWIDTH_1 = 1000;
    protected static final String SUB_FLOW_ID_1 = "test_sub_flow_1";
    protected static final String SUB_FLOW_ID_2 = "test_sub_flow_2";
    protected static final String SUB_FLOW_ID_SHARED = "shared";
    protected static InMemoryGraphPersistenceManager persistenceManager;
    protected static StatsTopologyConfig statsTopologyConfig;
    protected static TestKafkaConsumer otsdbConsumer;
    protected static FlowRepository flowRepository;
    protected static HaFlowRepository haFlowRepository;
    protected static HaSubFlowRepository haSubFlowRepository;
    protected static HaFlowPathRepository haFlowPathRepository;
    protected static FlowPathRepository flowPathRepository;
    protected static YFlowRepository yFlowRepository;
    protected static SwitchRepository switchRepository;
    protected static final SwitchId SWITCH_ID_1 = new SwitchId(1L);
    protected static final SwitchId SWITCH_ID_2 = new SwitchId(2L);
    protected static final SwitchId SWITCH_ID_3 = new SwitchId(3L);
    protected static final SwitchId SWITCH_ID_4 = new SwitchId(4L);
    protected static final SwitchId SWITCH_ID_5 = new SwitchId(5L);
    protected static final SwitchId SWITCH_ID_6 = new SwitchId(6L);
    protected static final SwitchId SWITCH_ID_7 = new SwitchId(7L);
    protected static final PathId PATH_ID_1 = new PathId("path_1");
    protected static final PathId PATH_ID_2 = new PathId("path_2");
    protected static final int PORT_1 = 1;
    protected static final int PORT_2 = 2;
    protected static final int PORT_3 = 3;
    protected static final int PORT_4 = 4;
    protected static final MeterId METER_ID_HA_FLOW_PATH_FORWARD = new MeterId(11);
    protected static final MeterId METER_ID_HA_FLOW_PATH_REVERSE = new MeterId(14);
    protected static final MeterId METER_ID_REVERSE_SUB_PATH_1 = new MeterId(14);
    protected static final MeterId METER_ID_REVERSE_SUB_PATH_2 = new MeterId(14);
    protected static final GroupId GROUP_ID_1 = new GroupId(15);
    protected static final PathId SUB_PATH_ID_1 = new PathId("sub_path_id_1");
    protected static final PathId SUB_PATH_ID_2 = new PathId("sub_path_id_2");
    protected static final PathId SUB_PATH_ID_3 = new PathId("sub_path_id_3");
    protected static final PathId SUB_PATH_ID_4 = new PathId("sub_path_id_4");
    protected static final MeterId SHARED_POINT_METER_ID = new MeterId(4);
    protected static final MeterId Y_POINT_METER_ID = new MeterId(5);
    protected static final FlowSegmentCookie COOKIE_FORWARD = FlowSegmentCookie.builder().flowEffectiveId(1)
            .direction(FlowPathDirection.FORWARD).build();
    protected static final FlowSegmentCookie COOKIE_FORWARD_SUBFLOW_1 = COOKIE_FORWARD.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1).build();
    protected static final FlowSegmentCookie COOKIE_FORWARD_SUBFLOW_2 = COOKIE_FORWARD.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2).build();
    protected static final FlowSegmentCookie COOKIE_REVERSE = FlowSegmentCookie.builder().flowEffectiveId(2)
            .direction(FlowPathDirection.REVERSE).build();

    protected static final FlowSegmentCookie COOKIE_REVERSE_SUBFLOW_1 = COOKIE_REVERSE.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_1).build();
    protected static final FlowSegmentCookie COOKIE_REVERSE_SUBFLOW_2 = COOKIE_REVERSE.toBuilder()
            .subType(FlowSegmentCookie.FlowSubType.HA_SUB_FLOW_2).build();
    protected static final long MAIN_COOKIE = 15;
    protected static final long PROTECTED_COOKIE = 17;
    protected static final FlowSegmentCookie MAIN_FORWARD_COOKIE = new FlowSegmentCookie(FORWARD, MAIN_COOKIE);
    protected static final FlowSegmentCookie MAIN_REVERSE_COOKIE = new FlowSegmentCookie(REVERSE, MAIN_COOKIE);
    protected static final FlowSegmentCookie PROTECTED_FORWARD_COOKIE = new FlowSegmentCookie(
            FORWARD, PROTECTED_COOKIE);
    protected static final FlowSegmentCookie PROTECTED_REVERSE_COOKIE = new FlowSegmentCookie(
            REVERSE, PROTECTED_COOKIE);
    protected static final FlowSegmentCookie FORWARD_MIRROR_COOKIE = MAIN_FORWARD_COOKIE.toBuilder()
            .mirror(true).build();
    protected static final FlowSegmentCookie REVERSE_MIRROR_COOKIE = MAIN_REVERSE_COOKIE.toBuilder()
            .mirror(true).build();
    protected static final FlowSegmentCookie Y_MAIN_FORWARD_COOKIE = MAIN_FORWARD_COOKIE.toBuilder()
            .yFlow(true).build();
    protected static final FlowSegmentCookie Y_MAIN_REVERSE_COOKIE = MAIN_REVERSE_COOKIE.toBuilder()
            .yFlow(true).build();
    protected static final FlowSegmentCookie STAT_VLAN_FORWARD_COOKIE_1 = MAIN_FORWARD_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_1).build();
    protected static final FlowSegmentCookie STAT_VLAN_FORWARD_COOKIE_2 = MAIN_FORWARD_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_2).build();
    protected static final FlowSegmentCookie STAT_VLAN_REVERSE_COOKIE_1 = MAIN_REVERSE_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_1).build();
    protected static final FlowSegmentCookie STAT_VLAN_REVERSE_COOKIE_2 = MAIN_REVERSE_COOKIE.toBuilder()
            .type(CookieType.VLAN_STATS_PRE_INGRESS).statsVlan(STAT_VLAN_2).build();
    protected static final String FLOW_ID = "f253423454343";
    protected static final String Y_FLOW_ID = "Y_flow_1";
    protected static final String HA_FLOW_ID_1 = "test_ha_flow_1";
    protected static final String HA_FLOW_ID_2 = "test_ha_flow_2";
    protected static final String HA_FLOW_ID_3 = "test_ha_flow_3";
    protected static final String HA_FLOW_ID_4 = "test_ha_flow_4";
    protected static final String HA_FLOW_ID_5 = "test_ha_flow_5";

    protected static void setupOnce(int zookeeperPort, int kafkaPort) throws Exception {
        Properties configOverlay = getZooKeeperProperties(zookeeperPort, ROOT_NODE);
        configOverlay.putAll(getKafkaProperties(kafkaPort));
        configOverlay.setProperty("opentsdb.metric.prefix", METRIC_PREFIX);

        AbstractStormTest.startZooKafka(configOverlay);
        setStartSignal(zookeeperPort, ROOT_NODE, COMPONENT_NAME, RUN_ID);
        AbstractStormTest.startStorm(COMPONENT_NAME, RUN_ID);

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        launchEnvironment.setupOverlay(configOverlay);

        persistenceManager = InMemoryGraphPersistenceManager.newInstance();
        persistenceManager.install();

        StatsTopology statsTopology = new StatsTopology(launchEnvironment, persistenceManager);
        statsTopologyConfig = statsTopology.getConfig();

        StormTopology stormTopology = statsTopology.createTopology();
        Config config = stormConfig();

        cluster.submitTopology(StatsTopologyBaseTest.class.getSimpleName(), config, stormTopology);

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
    protected static void teardownOnce() throws Exception {
        otsdbConsumer.wakeup();
        otsdbConsumer.join();

        AbstractStormTest.stopZooKafkaAndStorm();
    }

    @BeforeEach
    public void setup() {
        otsdbConsumer.clear();
        persistenceManager.getInMemoryImplementation().purgeData();
    }

    protected static Switch createSwitch(SwitchId switchId) {
        Switch sw = Switch.builder().switchId(switchId).build();
        switchRepository.add(sw);
        return sw;
    }

    protected void sendStatsMessage(InfoData infoData) {
        InfoMessage infoMessage = new InfoMessage(infoData, timestamp, UUID.randomUUID().toString(),
                Destination.WFM_STATS, null);
        sendMessage(infoMessage, statsTopologyConfig.getKafkaStatsTopic());
    }

    protected void sendNotification(StatsNotification notification) {
        InfoMessage infoMessage = new InfoMessage(notification, timestamp, UUID.randomUUID().toString(),
                null, null);
        sendMessage(infoMessage, statsTopologyConfig.getFlowStatsNotifyTopic());
    }

    protected void sendMessage(Object object, String topic) {
        String request = null;
        try {
            request = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            fail("Unexpected error: " + e.getMessage());
        }
        kProducer.pushMessage(topic, request);
    }

    protected List<Datapoint> pollDatapoints(int expectedDatapointCount) {
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

    protected void assertMetric(long expected, String metricName, Map<String, Datapoint> datapointMap) {
        assertEquals(expected, datapointMap.get(METRIC_PREFIX + metricName).getValue().longValue());
    }

    protected Map<String, Datapoint> createDatapointMap(List<Datapoint> datapoints) {
        return datapoints
                .stream()
                .collect(Collectors.toMap(Datapoint::getMetric, Function.identity()));
    }

    protected Map<String, List<Datapoint>> createDatapointMapOfLists(List<Datapoint> datapoints) {
        return datapoints
                .stream()
                .collect(Collectors.groupingBy(Datapoint::getMetric));
    }
}
