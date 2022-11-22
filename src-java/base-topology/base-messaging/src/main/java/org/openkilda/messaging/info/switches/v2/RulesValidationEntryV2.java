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

package org.openkilda.messaging.info.switches.v2;

import static org.openkilda.messaging.Utils.getSize;
import static org.openkilda.messaging.Utils.joinBooleans;
import static org.openkilda.messaging.Utils.joinLists;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class RulesValidationEntryV2 implements Serializable {
    private boolean asExpected;
    private List<RuleInfoEntryV2> excess;
    private List<RuleInfoEntryV2> proper;
    private List<RuleInfoEntryV2> missing;
    private List<MisconfiguredInfo<RuleInfoEntryV2>> misconfigured;

    static RulesValidationEntryV2 join(List<RulesValidationEntryV2> entryList) {
        if (entryList == null) {
            return null;
        }

        List<RulesValidationEntryV2> nonNullEntries = entryList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (nonNullEntries.isEmpty()) {
            return null;
        }
        RulesValidationEntryV2Builder builder = RulesValidationEntryV2.builder();
        builder.asExpected(joinBooleans(nonNullEntries.stream().map(RulesValidationEntryV2::isAsExpected)
                .collect(Collectors.toList())));
        builder.excess(joinLists(nonNullEntries.stream().map(RulesValidationEntryV2::getExcess)));
        builder.proper(joinLists(nonNullEntries.stream().map(RulesValidationEntryV2::getProper)));
        builder.missing(joinLists(nonNullEntries.stream().map(RulesValidationEntryV2::getMissing)));
        builder.misconfigured(joinLists(nonNullEntries.stream().map(RulesValidationEntryV2::getMisconfigured)));
        return builder.build();
    }

    List<RulesValidationEntryV2> split(int firstChunkSize, int chunkSize) {
        List<RulesValidationEntryV2> result = new ArrayList<>();
        for (ValidationEntry<RuleInfoEntryV2> entry : ValidationEntry.split(
                firstChunkSize, chunkSize, missing, excess, proper, misconfigured)) {
            result.add(RulesValidationEntryV2.builder()
                    .asExpected(asExpected)
                    .missing(entry.getMissing())
                    .excess(entry.getExcess())
                    .proper(entry.getProper())
                    .misconfigured(entry.getMisconfigured())
                    .build());
        }
        return result;
    }

    int size() {
        return getSize(excess) + getSize(proper) + getSize(missing) + getSize(misconfigured);
    }
}
