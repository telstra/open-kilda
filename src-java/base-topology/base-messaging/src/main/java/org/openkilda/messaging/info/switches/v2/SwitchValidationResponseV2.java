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

import org.openkilda.messaging.Chunkable;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Value
@Builder(toBuilder = true)
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class SwitchValidationResponseV2 extends InfoData implements Chunkable<SwitchValidationResponseV2> {
    boolean asExpected;
    GroupsValidationEntryV2 groups;
    MetersValidationEntryV2 meters;
    RulesValidationEntryV2 rules;
    LogicalPortsValidationEntryV2 logicalPorts;

    @Override
    public List<SwitchValidationResponseV2> split(int chunkSize) {
        List<SwitchValidationResponseV2> result = new ArrayList<>();
        SwitchValidationResponseV2Builder current = SwitchValidationResponseV2.builder().asExpected(asExpected);
        boolean addCurrent = true;
        int currentSize = chunkSize;

        if (groups != null) {
            for (GroupsValidationEntryV2 group : groups.split(currentSize, chunkSize)) {
                current.groups(group);
                currentSize -= group.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    currentSize = chunkSize;
                    current = SwitchValidationResponseV2.builder().asExpected(asExpected);
                    addCurrent = false;
                }
            }
        }

        if (logicalPorts != null) {
            for (LogicalPortsValidationEntryV2 logicalPort : logicalPorts.split(currentSize, chunkSize)) {
                current.logicalPorts(logicalPort);
                currentSize -= logicalPort.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    currentSize = chunkSize;
                    current = SwitchValidationResponseV2.builder().asExpected(asExpected);
                    addCurrent = false;
                }
            }
        }

        if (meters != null) {
            for (MetersValidationEntryV2 meter : meters.split(currentSize, chunkSize)) {
                current.meters(meter);
                currentSize -= meter.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    currentSize = chunkSize;
                    current = SwitchValidationResponseV2.builder().asExpected(asExpected);
                    addCurrent = false;
                }
            }
        }

        if (rules != null) {
            for (RulesValidationEntryV2 rule : rules.split(currentSize, chunkSize)) {
                current.rules(rule);
                currentSize -= rule.size();
                addCurrent = true;
                if (currentSize == 0) {
                    result.add(current.build());
                    currentSize = chunkSize;
                    current = SwitchValidationResponseV2.builder().asExpected(asExpected);
                    addCurrent = false;
                }
            }
        }

        if (addCurrent) {
            result.add(current.build());
        }

        return result;
    }

    /**
     * Unites several entities into one.
     */
    public static SwitchValidationResponseV2 unite(List<SwitchValidationResponseV2> dataList) {
        if (dataList == null) {
            return null;
        }
        List<SwitchValidationResponseV2> nonNullData = dataList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (nonNullData.isEmpty()) {
            return null;
        }
        SwitchValidationResponseV2Builder result = SwitchValidationResponseV2.builder();
        result.asExpected(Utils.joinBooleans(nonNullData.stream().map(SwitchValidationResponseV2::isAsExpected)
                .collect(Collectors.toList())));
        result.groups(GroupsValidationEntryV2.unite(nonNullData.stream()
                .map(SwitchValidationResponseV2::getGroups)
                .collect(Collectors.toList())));
        result.rules(RulesValidationEntryV2.unite(nonNullData.stream()
                .map(SwitchValidationResponseV2::getRules)
                .collect(Collectors.toList())));
        result.meters(MetersValidationEntryV2.unite(nonNullData.stream()
                .map(SwitchValidationResponseV2::getMeters)
                .collect(Collectors.toList())));
        result.logicalPorts(LogicalPortsValidationEntryV2.unite(nonNullData.stream()
                .map(SwitchValidationResponseV2::getLogicalPorts)
                .collect(Collectors.toList())));
        return result.build();
    }
}
