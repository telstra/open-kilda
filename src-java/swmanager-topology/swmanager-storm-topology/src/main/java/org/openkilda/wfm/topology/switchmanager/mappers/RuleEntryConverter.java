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

import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.WriteMetadata;
import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.match.FieldMatch;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public class RuleEntryConverter {
    public static final RuleEntryConverter INSTANCE = Mappers.getMapper(RuleEntryConverter.class);

    /**
     * Converts rule representation.
     */
    public RuleInfoEntryV2 toRuleEntry(FlowSpeakerData speakerData) {
        return RuleInfoEntryV2.builder()
                .cookie(Optional.ofNullable(speakerData.getCookie())
                        .map(CookieBase::getValue)
                        .orElse(null))
                .priority(speakerData.getPriority())
                .tableId(Optional.ofNullable(speakerData.getTable())
                        .map(OfTable::getTableId)
                        .orElse(null))
                .cookieKind("TBD")
                .cookieHex("TBD")
                .flags(Optional.ofNullable(speakerData.getFlags())
                        .map(f -> f.stream().map(OfFlowFlag::name)
                                .collect(Collectors.toList()))
                        .orElse(Collections.emptyList()))
                .instructions(convertInstructions(Optional.ofNullable(speakerData.getInstructions())
                        .orElse(Instructions.builder().build())))
                .match(convertMatch(Optional.ofNullable(speakerData.getMatch())
                        .orElse(Collections.emptySet())))
                .build();
    }

    private Map<String, RuleInfoEntryV2.FieldMatch> convertMatch(Set<FieldMatch> fieldMatches) {
        Map<String, RuleInfoEntryV2.FieldMatch> matches = new HashMap<>();

        for (FieldMatch fieldMatch : fieldMatches) {
            RuleInfoEntryV2.FieldMatch info = convertFieldMatch(fieldMatch);

            String fieldName = Optional.ofNullable(fieldMatch.getField())
                    .map(Field::name)
                    .orElse(null);
            matches.put(fieldName, info);
        }

        return matches;
    }

    private RuleInfoEntryV2.FieldMatch convertFieldMatch(FieldMatch fieldMatch) {
        return RuleInfoEntryV2.FieldMatch.builder()
                .mask(Optional.ofNullable(fieldMatch.getMask())
                        .orElse(null))
                .isMasked(Optional.of(fieldMatch.isMasked())
                        .orElse(null))
                .value(Optional.of(fieldMatch.getValue())
                        .orElse(null))
                .build();
    }

    private RuleInfoEntryV2.Instructions convertInstructions(Instructions instructions) {
        return RuleInfoEntryV2.Instructions.builder()
                .goToTable(Optional.ofNullable(instructions.getGoToTable())
                        .map(OfTable::getTableId)
                        .orElse(null))
                .goToMeter(Optional.ofNullable(instructions.getGoToMeter())
                        .map(MeterId::getValue)
                        .orElse(null))
                .writeMetadata(convertWriteMetadata(Optional.ofNullable(instructions.getWriteMetadata())
                        .orElse(null)))
                .applyActions(convertActions(Optional.ofNullable(instructions.getApplyActions())
                        .orElse(Collections.emptyList())))
                .writeActions(convertActions(Optional.ofNullable(instructions.getWriteActions())
                        .orElse(Collections.emptySet())))
                .build();
    }

    private WriteMetadata convertWriteMetadata(OfMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return WriteMetadata.builder()
                .mask(metadata.getMask())
                .value(metadata.getValue())
                .build();
    }

    private List<String> convertActions(Collection<Action> actions) {
        return actions.stream()
                .map(e -> e.getType().name())
                .collect(Collectors.toList());
    }
}
