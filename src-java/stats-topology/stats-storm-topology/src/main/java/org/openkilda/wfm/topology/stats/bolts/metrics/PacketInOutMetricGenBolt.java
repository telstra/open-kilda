/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.bolts.metrics;

import static org.openkilda.wfm.topology.stats.StatsTopology.STATS_FIELD;

import org.openkilda.messaging.info.grpc.GetPacketInOutStatsResponse;
import org.openkilda.messaging.model.grpc.PacketInOutStatsDto;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

public class PacketInOutMetricGenBolt extends MetricGenBolt {

    public PacketInOutMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        GetPacketInOutStatsResponse response = pullValue(input, STATS_FIELD, GetPacketInOutStatsResponse.class);
        emit(response);
    }

    private void emit(GetPacketInOutStatsResponse response) {
        long timestamp = getCommandContext().getCreateTime();
        PacketInOutStatsDto stats = response.getStats();

        Map<String, String> tags = ImmutableMap.of("switchid", response.getSwitchId().toOtsdFormat());

        emitMetric("switch.packet-in.total-packets.dataplane", timestamp,
                stats.getPacketInTotalPacketsDataplane(), tags);
        emitMetric("switch.packet-in.total-packets", timestamp, stats.getPacketInTotalPackets(), tags);
        emitMetric("switch.packet-in.no-match.packets", timestamp, stats.getPacketInNoMatchPackets(), tags);
        emitMetric("switch.packet-in.apply-action.packets", timestamp, stats.getPacketInApplyActionPackets(), tags);
        emitMetric("switch.packet-in.invalid-ttl.packets", timestamp, stats.getPacketInInvalidTtlPackets(), tags);
        emitMetric("switch.packet-in.action-set.packets", timestamp, stats.getPacketInActionSetPackets(), tags);
        emitMetric("switch.packet-in.group.packets", timestamp, stats.getPacketInGroupPackets(), tags);
        emitMetric("switch.packet-in.packet-out.packets", timestamp, stats.getPacketInPacketOutPackets(), tags);
        emitMetric("switch.packet-out.total-packets.host", timestamp, stats.getPacketOutTotalPacketsHost(), tags);
        emitMetric("switch.packet-out.total-packets.dataplane", timestamp,
                stats.getPacketOutTotalPacketsDataplane(), tags);
        if (stats.getPacketOutEth0InterfaceUp() != null) {
            emitMetric("switch.packet-out.eth0-interface-up", timestamp,
                    stats.getPacketOutEth0InterfaceUp() ? 1 : 0, tags);
        }
    }
}
