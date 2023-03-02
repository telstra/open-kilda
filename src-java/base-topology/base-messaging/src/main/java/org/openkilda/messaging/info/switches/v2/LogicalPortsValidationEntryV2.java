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
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class LogicalPortsValidationEntryV2 implements Serializable {
    private boolean asExpected;
    private String error;
    private List<LogicalPortInfoEntryV2> excess;
    private List<LogicalPortInfoEntryV2> proper;
    private List<LogicalPortInfoEntryV2> missing;
    private List<MisconfiguredInfo<LogicalPortInfoEntryV2>> misconfigured;

    static LogicalPortsValidationEntryV2 join(List<LogicalPortsValidationEntryV2> entryList) {
        if (entryList == null) {
            return null;
        }

        List<LogicalPortsValidationEntryV2> nonNullEntries = entryList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (nonNullEntries.isEmpty()) {
            return null;
        }
        LogicalPortsValidationEntryV2Builder builder = LogicalPortsValidationEntryV2.builder();
        builder.asExpected(joinBooleans(nonNullEntries.stream().map(LogicalPortsValidationEntryV2::isAsExpected)
                .collect(Collectors.toList())));
        builder.excess(joinLists(nonNullEntries.stream().map(LogicalPortsValidationEntryV2::getExcess)));
        builder.proper(joinLists(nonNullEntries.stream().map(LogicalPortsValidationEntryV2::getProper)));
        builder.missing(joinLists(nonNullEntries.stream().map(LogicalPortsValidationEntryV2::getMissing)));
        builder.misconfigured(joinLists(nonNullEntries.stream().map(LogicalPortsValidationEntryV2::getMisconfigured)));
        nonNullEntries.stream().filter(e -> StringUtils.isNoneBlank(e.error)).findFirst()
                .map(LogicalPortsValidationEntryV2::getError).ifPresent(builder::error);
        return builder.build();
    }

    List<LogicalPortsValidationEntryV2> split(int firstChunkSize, int chunkSize) {
        List<LogicalPortsValidationEntryV2> result = new ArrayList<>();
        for (ValidationEntry<LogicalPortInfoEntryV2> entry : ValidationEntry.split(
                firstChunkSize, chunkSize, missing, excess, proper, misconfigured)) {
            result.add(LogicalPortsValidationEntryV2.builder()
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
