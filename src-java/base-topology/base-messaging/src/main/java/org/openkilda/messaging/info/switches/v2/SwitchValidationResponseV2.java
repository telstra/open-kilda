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

import static org.openkilda.messaging.Utils.getNonNullEntries;

import org.openkilda.messaging.Chunkable;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.split.SplitIterator;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
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
        SplitIterator<SwitchValidationResponseV2, SwitchValidationResponseV2Builder> iterator = new SplitIterator<>(
                chunkSize, chunkSize, SwitchValidationResponseV2Builder::build,
                () -> SwitchValidationResponseV2.builder().asExpected(asExpected));

        if (groups != null) {
            for (GroupsValidationEntryV2 group : groups.split(iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().groups(group);
                iterator.next(group.size()).ifPresent(result::add);
            }
        }

        if (logicalPorts != null) {
            for (LogicalPortsValidationEntryV2 logicalPort : logicalPorts.split(
                    iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().logicalPorts(logicalPort);
                iterator.next(logicalPort.size()).ifPresent(result::add);
            }
        }

        if (meters != null) {
            for (MetersValidationEntryV2 meter : meters.split(iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().meters(meter);
                iterator.next(meter.size()).ifPresent(result::add);
            }
        }

        if (rules != null) {
            for (RulesValidationEntryV2 rule : rules.split(iterator.getRemainingChunkSize(), chunkSize)) {
                iterator.getCurrentBuilder().rules(rule);
                iterator.next(rule.size()).ifPresent(result::add);
            }
        }

        if (iterator.isAddCurrent()) {
            result.add(iterator.getCurrentBuilder().build());
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
        List<SwitchValidationResponseV2> nonNullData = getNonNullEntries(dataList);
        if (nonNullData.isEmpty()) {
            return null;
        }
        SwitchValidationResponseV2Builder result = SwitchValidationResponseV2.builder();
        result.asExpected(Utils.joinBooleans(nonNullData.stream().map(SwitchValidationResponseV2::isAsExpected)
                .collect(Collectors.toList())));
        result.groups(GroupsValidationEntryV2.join(nonNullData.stream()
                .map(SwitchValidationResponseV2::getGroups)
                .collect(Collectors.toList())));
        result.rules(RulesValidationEntryV2.join(nonNullData.stream()
                .map(SwitchValidationResponseV2::getRules)
                .collect(Collectors.toList())));
        result.meters(MetersValidationEntryV2.join(nonNullData.stream()
                .map(SwitchValidationResponseV2::getMeters)
                .collect(Collectors.toList())));
        result.logicalPorts(LogicalPortsValidationEntryV2.join(nonNullData.stream()
                .map(SwitchValidationResponseV2::getLogicalPorts)
                .collect(Collectors.toList())));
        return result.build();
    }
}
