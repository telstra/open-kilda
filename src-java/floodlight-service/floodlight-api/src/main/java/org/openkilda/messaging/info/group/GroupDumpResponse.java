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

package org.openkilda.messaging.info.group;

import static org.openkilda.messaging.Utils.joinLists;

import org.openkilda.messaging.Chunkable;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.SpeakerData;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class GroupDumpResponse extends InfoData implements Chunkable<GroupDumpResponse> {

    List<GroupSpeakerData> groupSpeakerData;
    SwitchId switchId;

    @Override
    public List<GroupDumpResponse> split(int chunkSize) {
        return Utils.split(groupSpeakerData, chunkSize).stream()
                .map(groupSpeakerData1 -> new GroupDumpResponse(groupSpeakerData1, this.switchId))
                .collect(Collectors.toList());
    }

    /**
     * Unites several responses into one.
     */
    public static GroupDumpResponse unite(List<GroupDumpResponse> dataList) {
        if (dataList == null) {
            return null;
        }

        SwitchId swId = dataList.stream().flatMap(groupDumpResponse -> groupDumpResponse.getGroupSpeakerData().stream())
                .map(SpeakerData::getSwitchId).findFirst().orElse(null);

        return new GroupDumpResponse(joinLists(dataList.stream()
                .filter(Objects::nonNull)
                .map(GroupDumpResponse::getGroupSpeakerData)), swId);
    }
}
