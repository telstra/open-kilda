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

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.wfm.config.KafkaConfig.STATS_TOPOLOGY_TEST_KAFKA_PORT_1;
import static org.openkilda.wfm.config.ZookeeperConfig.STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT_1;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.grpc.GetPacketInOutStatsResponse;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.messaging.info.stats.MeterConfigReply;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.SwitchTableStatsData;
import org.openkilda.messaging.info.stats.TableStatsEntry;
import org.openkilda.messaging.model.grpc.PacketInOutStatsDto;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Disabled("Sleep method from Storm Utils causes gradle to exit. Without the sleep, the test fails because there are"
        + "no records when polling from the speaker See https://github.com/telstra/open-kilda/issues/5563")
public class StatsTopologySwitchTest extends StatsTopologyBaseTest {

    @BeforeAll
    public static void setupOnce() throws Exception {
        StatsTopologyBaseTest.setupOnce(STATS_TOPOLOGY_TEST_ZOOKEEPER_PORT_1, STATS_TOPOLOGY_TEST_KAFKA_PORT_1);
    }

    @BeforeEach
    public void setup() {
        super.setup();
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
}
