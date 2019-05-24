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
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFMeterStats;
import org.projectfloodlight.openflow.protocol.OFMeterStatsReply;

import java.util.List;
import java.util.Objects;

@Mapper
@Slf4j
public abstract class OfMeterStatsMapper {

    public static final OfMeterStatsMapper INSTANCE = Mappers.getMapper(OfMeterStatsMapper.class);

    /**
     * Convert list of {@link OFMeterStatsReply} to {@link MeterStatsData}.
     * @param data list of meter stats replies to be converted.
     * @param switchId id of the switch from which these replies were gotten.
     * @return result of transformation {@link MeterStatsData}.
     */
    public MeterStatsData toMeterStatsData(List<OFMeterStatsReply> data, SwitchId switchId) {
        try {
            List<MeterStatsEntry> stats = data.stream()
                    .flatMap(reply -> reply.getEntries().stream())
                    .map(this::toMeterStatsEntry)
                    .filter(Objects::nonNull)
                    .collect(toList());
            return new MeterStatsData(switchId, stats);
        } catch (NullPointerException | UnsupportedOperationException | IllegalArgumentException e) {
            log.error(String.format("Could not convert meter stats data %s on switch %s", data, switchId), e);
            return null;
        }
    }

    /**
     * Convert {@link OFMeterStats} to {@link MeterStatsEntry}.
     * @param entry meter stats entry to be converted.
     * @return result of transformation {@link MeterStatsEntry} or null if object couldn't be parsed.
     */
    public MeterStatsEntry toMeterStatsEntry(OFMeterStats entry) {
        try {

            if (entry.getBandStats().size() > 1) {
                log.warn("Meter '{}' has more than one meter band. Only first band will be handled. "
                        + "Several bands are not supported.", entry.getMeterId());
            }

            long byteBandCount = 0;
            long packetBandCount = 0;

            if (!entry.getBandStats().isEmpty()) {
                byteBandCount = entry.getBandStats().get(0).getByteBandCount().getValue();
                packetBandCount = entry.getBandStats().get(0).getPacketBandCount().getValue();
            }

            return new MeterStatsEntry(entry.getMeterId(), byteBandCount, packetBandCount);
        } catch (NullPointerException | UnsupportedOperationException | IllegalArgumentException e) {
            log.error(String.format("Could not convert OFMeterStats object %s", entry), e);
            return null;
        }
    }
}
