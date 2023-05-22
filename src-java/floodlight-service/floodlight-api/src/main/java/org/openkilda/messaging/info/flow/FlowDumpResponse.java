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

package org.openkilda.messaging.info.flow;

import static org.openkilda.messaging.Utils.joinLists;

import org.openkilda.messaging.Chunkable;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerData;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class FlowDumpResponse extends InfoData implements Chunkable<FlowDumpResponse> {

    List<FlowSpeakerData> flowSpeakerData;
    SwitchId switchId;

    @Override
    public List<FlowDumpResponse> split(int chunkSize) {
        return Utils.split(flowSpeakerData, chunkSize).stream()
                .map(flowSpeakerDataList -> new FlowDumpResponse(flowSpeakerDataList, this.switchId))
                .collect(Collectors.toList());
    }

    /**
     * Unites several responses into one.
     */
    public static FlowDumpResponse unite(List<FlowDumpResponse> dataList) {
        if (dataList == null) {
            return null;
        }

        return new FlowDumpResponse(joinLists(dataList.stream()
                .filter(Objects::nonNull)
                .map(FlowDumpResponse::getFlowSpeakerData)),
                dataList.stream().findFirst().map(FlowDumpResponse::getSwitchId).orElse(null));
    }
}
