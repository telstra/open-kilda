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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonNaming(value = SnakeCaseStrategy.class)
public class GroupsValidationEntryV2 implements Serializable {
    private boolean asExpected;
    private List<GroupInfoEntryV2> excess;
    private List<GroupInfoEntryV2> proper;
    private List<GroupInfoEntryV2> missing;
    private List<MisconfiguredInfo<GroupInfoEntryV2>> misconfigured;

    /**
     * Unites several entries into one.
     */
    public static GroupsValidationEntryV2 unite(List<GroupsValidationEntryV2> entryList) {
        if (entryList == null) {
            return null;
        }

        List<GroupsValidationEntryV2> nonNullList = entryList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (nonNullList.isEmpty()) {
            return null;
        }
        GroupsValidationEntryV2Builder builder = GroupsValidationEntryV2.builder();
        builder.asExpected(joinBooleans(nonNullList.stream().map(GroupsValidationEntryV2::isAsExpected)
                .collect(Collectors.toList())));
        builder.excess(joinLists(nonNullList.stream().map(GroupsValidationEntryV2::getExcess)));
        builder.proper(joinLists(nonNullList.stream().map(GroupsValidationEntryV2::getProper)));
        builder.missing(joinLists(nonNullList.stream().map(GroupsValidationEntryV2::getMissing)));
        builder.misconfigured(joinLists(nonNullList.stream().map(GroupsValidationEntryV2::getMisconfigured)));
        return builder.build();
    }

    /**
     * Splits entry.
     */
    public List<GroupsValidationEntryV2> split(int firstChunkSize, int chunkSize) {
        List<GroupsValidationEntryV2> result = new ArrayList<>();
        GroupsValidationEntryV2Builder current = GroupsValidationEntryV2.builder().asExpected(asExpected);
        int currentSize = firstChunkSize;

        if (excess != null) {
            for (List<GroupInfoEntryV2> entry : Utils.split(excess, currentSize, chunkSize)) {
                current.excess(entry);
                currentSize -= entry.size();
                if (currentSize == 0) {
                    result.add(current.build());
                    current = GroupsValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                }
            }
        }

        if (proper != null) {
            for (List<GroupInfoEntryV2> entry : Utils.split(proper, currentSize, chunkSize)) {
                current.proper(entry);
                currentSize -= entry.size();
                if (currentSize == 0) {
                    result.add(current.build());
                    current = GroupsValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                }
            }
        }

        if (missing != null) {
            for (List<GroupInfoEntryV2> entry : Utils.split(missing, currentSize, chunkSize)) {
                current.missing(entry);
                currentSize -= entry.size();
                if (currentSize == 0) {
                    result.add(current.build());
                    current = GroupsValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                }
            }
        }

        if (misconfigured != null) {
            for (List<MisconfiguredInfo<GroupInfoEntryV2>> entry : Utils.split(misconfigured, currentSize, chunkSize)) {
                current.misconfigured(entry);
                currentSize -= entry.size();
                if (currentSize == 0) {
                    result.add(current.build());
                    current = GroupsValidationEntryV2.builder().asExpected(asExpected);
                    currentSize = chunkSize;
                }
            }
        }

        if (result.isEmpty() || currentSize < chunkSize) {
            result.add(current.build());
        }

        return result;
    }

    public int size() {
        return getSize(excess) + getSize(proper) + getSize(missing) + getSize(misconfigured);
    }
}
