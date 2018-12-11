/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.converter;

import static java.util.stream.Collectors.toList;

import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.messaging.info.stats.MeterStatsReply;
import org.openkilda.model.SwitchId;

import org.projectfloodlight.openflow.protocol.OFMeterStats;
import org.projectfloodlight.openflow.protocol.OFMeterStatsReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class OfMeterStatsConverter {
    private static final Logger logger = LoggerFactory.getLogger(OfMeterStatsConverter.class);

    private OfMeterStatsConverter() {}

    /**
     * Convert list of {@link OFMeterStatsReply} to {@link MeterStatsData}.
     * @param data list of meter stats replies to be converted.
     * @param switchId id of the switch from which these replies were gotten.
     * @return result of transformation {@link MeterStatsData}.
     */
    public static MeterStatsData toMeterStatsData(List<OFMeterStatsReply> data, SwitchId switchId) {
        List<MeterStatsReply> replies = data.stream()
                .map(reply -> toMeterStatsReply(reply, switchId))
                .collect(toList());
        return new MeterStatsData(switchId, replies);
    }

    private static MeterStatsReply toMeterStatsReply(OFMeterStatsReply reply, SwitchId switchId) {
        List<MeterStatsEntry> entries = reply.getEntries().stream()
                .map(entry -> toMeterStatsEntry(entry, switchId))
                .collect(toList());
        return new MeterStatsReply(entries);
    }

    private static MeterStatsEntry toMeterStatsEntry(OFMeterStats entry, SwitchId switchId) {
        if (entry.getBandStats().size() > 1) {
            logger.warn("Meter '{}' on switch '{}' has more than one meter band. "
                      + "Only first band will be handled. Several bands are not supported.",
                    entry.getMeterId(), switchId);
        }

        long byteBandCount = 0;
        long packetBandCount = 0;

        if (!entry.getBandStats().isEmpty()) {
            byteBandCount = entry.getBandStats().get(0).getByteBandCount().getValue();
            packetBandCount = entry.getBandStats().get(0).getPacketBandCount().getValue();
        }

        return new MeterStatsEntry(entry.getMeterId(), byteBandCount, packetBandCount);
    }
}
