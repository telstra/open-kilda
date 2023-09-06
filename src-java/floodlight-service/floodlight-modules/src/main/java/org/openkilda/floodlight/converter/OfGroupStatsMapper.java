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

package org.openkilda.floodlight.converter;

import static java.util.stream.Collectors.toList;

import org.openkilda.messaging.info.stats.GroupStatsData;
import org.openkilda.messaging.info.stats.GroupStatsEntry;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFGroupStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupStatsReply;

import java.util.List;
import java.util.Objects;

@Mapper
@Slf4j
public abstract class OfGroupStatsMapper {

    public static final OfGroupStatsMapper INSTANCE = Mappers.getMapper(OfGroupStatsMapper.class);

    /**
     * Convert list of {@link OFGroupStatsReply} to {@link GroupStatsData}.
     *
     * @param data     list of group stats replies to be converted.
     * @param switchId id of the switch from which these replies were gotten.
     * @return result of transformation {@link GroupStatsData}.
     */
    public GroupStatsData toGroupStatsData(List<OFGroupStatsReply> data, SwitchId switchId) {
        try {
            List<GroupStatsEntry> stats = data.stream()
                    .flatMap(reply -> reply.getEntries().stream())
                    .map(this::toGroupStatsEntry)
                    .filter(Objects::nonNull)
                    .collect(toList());
            return new GroupStatsData(switchId, stats);
        } catch (NullPointerException | UnsupportedOperationException | IllegalArgumentException e) {
            log.error(String.format("Could not convert group stats data %s on switch %s", data, switchId), e);
            return null;
        }
    }

    /**
     * Convert {@link OFGroupStatsEntry} to {@link GroupStatsEntry}.
     *
     * @param entry group stats entry to be converted.
     * @return result of transformation {@link GroupStatsEntry} or null if object couldn't be parsed.
     */
    public GroupStatsEntry toGroupStatsEntry(OFGroupStatsEntry entry) {
        try {
            return new GroupStatsEntry(entry.getGroup().getGroupNumber(),
                    entry.getBucketStats().get(0).getByteCount().getValue(),
                    entry.getBucketStats().get(0).getPacketCount().getValue());
        } catch (NullPointerException | UnsupportedOperationException | IllegalArgumentException e) {
            log.error(String.format("Could not convert OFGroupStats object %s", entry), e);
            return null;
        }
    }
}
