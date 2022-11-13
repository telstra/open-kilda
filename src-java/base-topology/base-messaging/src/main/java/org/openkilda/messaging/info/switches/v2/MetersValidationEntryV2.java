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

import org.openkilda.messaging.Utils;

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
public class MetersValidationEntryV2 implements Serializable {
    private boolean asExpected;
    private List<MeterInfoEntryV2> excess;
    private List<MeterInfoEntryV2> proper;
    private List<MeterInfoEntryV2> missing;
    private List<MisconfiguredInfo<MeterInfoEntryV2>> misconfigured;

    static MetersValidationEntryV2 unite(List<MetersValidationEntryV2> entryList) {
        if (entryList == null) {
            return null;
        }

        List<MetersValidationEntryV2> nonNullEntries = entryList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (nonNullEntries.isEmpty()) {
            return null;
        }
        MetersValidationEntryV2Builder builder = MetersValidationEntryV2.builder();
        builder.asExpected(joinBooleans(nonNullEntries.stream().map(MetersValidationEntryV2::isAsExpected)
                .collect(Collectors.toList())));
        builder.excess(joinLists(nonNullEntries.stream().map(MetersValidationEntryV2::getExcess)));
        builder.proper(joinLists(nonNullEntries.stream().map(MetersValidationEntryV2::getProper)));
        builder.missing(joinLists(nonNullEntries.stream().map(MetersValidationEntryV2::getMissing)));
        builder.misconfigured(joinLists(nonNullEntries.stream().map(MetersValidationEntryV2::getMisconfigured)));
        return builder.build();
    }

    List<MetersValidationEntryV2> split(int firstChunkSize, int chunkSize) {
        List<MetersValidationEntryV2> result = new ArrayList<>();
        MetersValidationEntryV2Builder current = MetersValidationEntryV2.builder().asExpected(asExpected);
        int currentSize = firstChunkSize;
        boolean addCurrent = true;

        if (excess != null) {
            for (List<MeterInfoEntryV2> entry : Utils.split(excess, currentSize, chunkSize)) {
                current.excess(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = MetersValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (proper != null) {
            for (List<MeterInfoEntryV2> entry : Utils.split(proper, currentSize, chunkSize)) {
                current.proper(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = MetersValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (missing != null) {
            for (List<MeterInfoEntryV2> entry : Utils.split(missing, currentSize, chunkSize)) {
                current.missing(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = MetersValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (misconfigured != null) {
            for (List<MisconfiguredInfo<MeterInfoEntryV2>> entry : Utils.split(
                    misconfigured, currentSize, chunkSize)) {
                current.misconfigured(entry);
                currentSize -= entry.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    current = MetersValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                    addCurrent = false;
                }
            }
        }

        if (addCurrent) {
            result.add(current.build());
        }

        return result;
    }

    int size() {
        return getSize(excess) + getSize(proper) + getSize(missing) + getSize(misconfigured);
    }
}
