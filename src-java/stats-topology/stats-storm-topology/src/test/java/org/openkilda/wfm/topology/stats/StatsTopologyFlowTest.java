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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.openkilda.wfm.config.KafkaConfig.STATS_TOPOLOGY_TEST_KAFKA_PORT_3;
import static org.openkilda.wfm.config.ZookeeperConfig.STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT_3;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.RemoveYFlowStatsInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateYFlowStatsInfo;
import org.openkilda.messaging.payload.yflow.YFlowEndpointResources;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.flow.TestFlowBuilder;
import org.openkilda.wfm.share.mappers.FlowPathMapper;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Disabled("Sleep method from Storm Utils causes gradle to exit. Without the sleep, the test fails because there are"
        + "no records when polling from the speaker See https://github.com/telstra/open-kilda/issues/5563")
public class StatsTopologyFlowTest extends StatsTopologyBaseTest {

    @BeforeAll
    public static void setupOnce() throws Exception {
        StatsTopologyBaseTest.setupOnce(STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT_3, STATS_TOPOLOGY_TEST_KAFKA_PORT_3);
    }

    @BeforeEach
    public void setup() {
        for (Flow flow : flowRepository.findAll()) {
            flow.getPaths().forEach(path -> sendRemoveFlowPathInfo(path, flow.getVlanStatistics(),
                    flow.getYFlowId(), flow.getYPointSwitchId()));
        }
        for (YFlow yFlow : yFlowRepository.findAll()) {
            sendRemoveYFlowPathInfo(yFlow);
        }
        super.setup();
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
    public void flowStatsTest() {
        Flow flow = createOneSwitchFlow(SWITCH_ID_1);
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());

        FlowStatsEntry flowStats = new FlowStatsEntry(1, MAIN_FORWARD_COOKIE.getValue(), 155L, 305L, 10, 10);

        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(flowStats)));
        validateFlowStats(flowStats, MAIN_FORWARD_COOKIE, SWITCH_ID_1, true, true);
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
    public void flowWithTwoSwitchesStatsTest() {
        Flow flow = createFlow();
        sendUpdateFlowPathInfo(flow.getForwardPath(), flow.getVlanStatistics());
        FlowStatsEntry forwardIngress = new FlowStatsEntry(1, flow.getForwardPath().getCookie().getValue(),
                160L, 300L, 10, 10);
        sendStatsMessage(new FlowStatsData(SWITCH_ID_1, Collections.singletonList(forwardIngress)));
        validateFlowStats(forwardIngress, flow.getForwardPath().getCookie(), SWITCH_ID_1, true, false);
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

    private void sendRemoveFlowPathInfo(FlowPath flowPath, Set<Integer> vlanStatistics, String yFlowId,
                                        SwitchId yPointSwitchId) {
        sendRemoveFlowPathInfo(flowPath, vlanStatistics, flowPath.hasIngressMirror(),
                flowPath.hasEgressMirror(), yFlowId, yPointSwitchId);
    }

    private void sendRemoveFlowPathInfo(
            FlowPath flowPath, Set<Integer> vlanStatistics, boolean ingressMirror,
            boolean egressMirror, String yFlowId, SwitchId yPointSwitchId) {
        RemoveFlowPathInfo pathInfo = new RemoveFlowPathInfo(
                flowPath.getFlowId(), yFlowId, yPointSwitchId, flowPath.getCookie(), flowPath.getMeterId(), null,
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
                null,
                FlowPathMapper.INSTANCE.mapToPathNodes(flowPath.getFlow(), flowPath), vlanStatistics, ingressMirror,
                egressMirror);
        sendNotification(pathInfo);
    }

    private void sendUpdateSubFlowPathInfo(FlowPath flowPath, Flow flow) {
        UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                flowPath.getFlowId(), flow.getYFlowId(), flow.getYPointSwitchId(), flowPath.getCookie(),
                flowPath.getMeterId(), null,
                FlowPathMapper.INSTANCE.mapToPathNodes(flowPath.getFlow(), flowPath),
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
}
