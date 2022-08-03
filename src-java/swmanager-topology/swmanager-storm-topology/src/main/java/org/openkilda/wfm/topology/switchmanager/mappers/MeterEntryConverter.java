/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.mappers;

import org.openkilda.messaging.info.switches.v2.MeterInfoEntryV2;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public class MeterEntryConverter {
    public static final MeterEntryConverter INSTANCE = Mappers.getMapper(MeterEntryConverter.class);

    /**
     * Converts meter representation.
     */
    public MeterInfoEntryV2 toMeterEntry(MeterSpeakerData meterSpeakerData) {
        return MeterInfoEntryV2.builder()
                .meterId(meterSpeakerData.getMeterId().getValue())
                .rate(meterSpeakerData.getRate())
                .burstSize(meterSpeakerData.getBurst())
                .flags(convertFlags(Optional.ofNullable(meterSpeakerData.getFlags()).orElse(Collections.emptySet())))
                .build();
    }

    private List<String> convertFlags(Set<MeterFlag> flags) {
        return flags.stream()
                .map(MeterFlag::name)
                .collect(Collectors.toList());
    }
}
