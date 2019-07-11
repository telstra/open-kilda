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

package org.openkilda.floodlight.converter;

import org.openkilda.messaging.info.stats.TableStatsEntry;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFTableStatsEntry;

@Mapper
public abstract class OfTableStatsMapper {
    public static final OfTableStatsMapper INSTANCE = Mappers.getMapper(OfTableStatsMapper.class);

    @Mapping(source = "tableId.value", target = "tableId")
    @Mapping(source = "activeCount", target = "activeEntries")
    @Mapping(source = "lookupCount.value", target = "lookupCount")
    @Mapping(source = "matchedCount.value", target = "matchedCount")
    public abstract TableStatsEntry toTableStatsEntry(OFTableStatsEntry source);
}
