/* Copyright 2023 Telstra Open Source
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlowPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildSegments;
import static org.openkilda.wfm.config.KafkaConfig.STATS_TOPOLOGY_TEST_KAFKA_PORT_2;
import static org.openkilda.wfm.config.ZookeeperConfig.STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT_2;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.GroupStatsData;
import org.openkilda.messaging.info.stats.GroupStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.RemoveHaFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateHaFlowPathInfo;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.share.mappers.FlowPathMapper;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Disabled("Sleep method from Storm Utils causes gradle to exit. Without the sleep, the test fails because there are"
        + "no records when polling from the speaker See https://github.com/telstra/open-kilda/issues/5563")
public class StatsTopologyHaFlowTest extends StatsTopologyBaseTest {

    @BeforeAll
    public static void setupOnce() throws Exception {
        StatsTopologyBaseTest.setupOnce(STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT_2, STATS_TOPOLOGY_TEST_KAFKA_PORT_2);
    }

    @BeforeEach
    public void setup() {
        for (HaFlow haFlow : haFlowRepository.findAll()) {
            haFlow.getPaths().forEach(haFlowPath -> haFlowPath.getSubPaths()
                    .forEach(flowPath -> sendRemoveHaFlowPathInfo(flowPath, haFlow)));
        }
        super.setup();
    }

    /**
     * 4 switch ha-flow flow-stats test.
     * ''''''''/-4--5
     * 1--2--3
     * '''''''\----6
     */
    @Test
    public void haFlowFakeStatsTest() {
        HaFlow haFlow = createHaFlowYShape();
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
        HaFlow haFlow = createHaFlowYShape();
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
        HaFlow haFlow = createHaFlowYShape();
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
        HaFlow haFlow = createHaFlowYShape();
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
    public void haFlowVShapeStatsTest() {
        HaFlow haFlow = createHaFlowVShape();
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
    public void haFlowMeterVShapeStatsTest() {
        HaFlow haFlow = createHaFlowVShape();
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
    public void haFlowGroupVShapeStatsTest() {
        HaFlow haFlow = createHaFlowVShape();
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
    public void haFlowIShape3SwitchesStatsTest() {
        HaFlow haFlow = createHaFlowIShape3Switches();

        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                170L, 320L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateHaFlowStats(HA_FLOW_ID_3, forwardIngress, haFlow.getForwardPath().getCookie(), SWITCH_ID_1,
                true, false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                171L, 321L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_3, forwardYPoint, haFlow.getForwardPath().getCookie(), SWITCH_ID_2,
                false, true, true, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardEgressPoint1 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                172L, 322L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(forwardEgressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_3, forwardEgressPoint1, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_3,
                false, true, false, SUB_FLOW_ID_1);

        //reverse:
        FlowSegmentCookie cookieReverse = haFlow.getReversePath().getCookie();

        FlowStatsEntry reverseIngressPoint1 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                173L, 323L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_3, Collections.singletonList(reverseIngressPoint1)));
        validateHaFlowStats(HA_FLOW_ID_3, reverseIngressPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_3,
                true, false, false, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint1 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_1.getValue(),
                174L, 324L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint1)));
        validateHaFlowStats(HA_FLOW_ID_3, reverseYPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_2,
                false, false, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseEgress = new FlowStatsEntry(1, cookieReverse.getValue(),
                175L, 325L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(reverseEgress)));
        validateHaFlowStats(HA_FLOW_ID_3, reverseEgress, cookieReverse, SWITCH_ID_1,
                false, true, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1, COOKIE_REVERSE_SUBFLOW_2.getValue(),
                176L, 326L, 10, 10);
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
    public void haFlowMeterIShape3SwitchesStatsTest() {
        HaFlow haFlow = createHaFlowIShape3Switches();
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
    public void haFlowIShape2SwitchesStatsTest() {
        HaFlow haFlow = createHaFlowIShape2Switches();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                149L, 299L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateHaFlowStats(HA_FLOW_ID_4, forwardIngress, haFlow.getForwardPath().getCookie(), SWITCH_ID_1, true,
                false, false, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, haFlow.getForwardPath().getCookie().getValue(),
                151L, 301L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_4, forwardYPoint, haFlow.getForwardPath().getCookie(), SWITCH_ID_2, false,
                true, true, SUB_FLOW_ID_SHARED);


        //reverse:
        FlowSegmentCookie cookieReverse = haFlow.getReversePath().getCookie();

        FlowStatsEntry reverseYPoint1 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_1.getValue(),
                152L, 302L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint1)));
        validateHaFlowStats(HA_FLOW_ID_4, reverseYPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_2, true,
                false, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_2.getValue(),
                153L, 303L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseYPoint2)));
        validateHaFlowStats(HA_FLOW_ID_4, reverseYPoint2, COOKIE_REVERSE_SUBFLOW_2, SWITCH_ID_2, true,
                false, true, SUB_FLOW_ID_2);

        FlowStatsEntry reverseEgress = new FlowStatsEntry(1, cookieReverse.getValue(),
                154L, 304L, 10, 10);
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
    public void haFlowIShape2SwitchesMeterStatsTest() {
        HaFlow haFlow = createHaFlowIShape2Switches();
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
    public void haFlowIShapeSharedStatsTest() {
        HaFlow haFlow = createHaFlowIShapeShared();
        sendUpdateHaFlowPathInfo(haFlow);
        //forward
        FlowStatsEntry forwardYPoint = new FlowStatsEntry(1, COOKIE_FORWARD.getValue(),
                160L, 310L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardYPoint)));
        validateHaFlowStats(HA_FLOW_ID_5, forwardYPoint, COOKIE_FORWARD, SWITCH_ID_1,
                true, true, true, SUB_FLOW_ID_SHARED);

        FlowStatsEntry forwardEgress1 = new FlowStatsEntry(1, COOKIE_FORWARD_SUBFLOW_1.getValue(),
                161L, 311L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(forwardEgress1)));
        validateHaFlowStats(HA_FLOW_ID_5, forwardEgress1, COOKIE_FORWARD_SUBFLOW_1, SWITCH_ID_2,
                false, true, false, SUB_FLOW_ID_1);


        //reverse:

        FlowStatsEntry reverseIngress = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_1.getValue(), 162L, 312L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_2, Collections.singletonList(reverseIngress)));
        validateHaFlowStats(HA_FLOW_ID_5, reverseIngress, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_2, true,
                false, false, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint1 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_1.getValue(),
                163L, 313L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(reverseYPoint1)));
        validateHaFlowStats(HA_FLOW_ID_5, reverseYPoint1, COOKIE_REVERSE_SUBFLOW_1, SWITCH_ID_1, false,
                true, true, SUB_FLOW_ID_1);

        FlowStatsEntry reverseYPoint2 = new FlowStatsEntry(1,
                COOKIE_REVERSE_SUBFLOW_2.getValue(),
                164L, 314L, 10, 10);
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
    public void haFlowIShapeSharedMeterStatsTest() {
        HaFlow haFlow = createHaFlowIShapeShared();
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

    /* This method will create the ha-flow with the following structure.
     *        /-4--5
     *1--2--3
     *        \----6
     * @return HaFlow.class
     */
    private HaFlow createHaFlowYShape() {
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
    private HaFlow createHaFlowVShape() {
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
    private HaFlow createHaFlowIShape3Switches() {
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
    private HaFlow createHaFlowIShape2Switches() {
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
    private HaFlow createHaFlowIShapeShared() {
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
