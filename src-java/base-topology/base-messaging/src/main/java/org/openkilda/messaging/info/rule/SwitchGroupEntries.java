/* Copyright 2020 Telstra Open Source
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

package org.openkilda.messaging.info.rule;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class SwitchGroupEntries extends InfoData {

    private SwitchId switchId;
    private List<GroupEntry> groupEntries;

    @JsonCreator
    public SwitchGroupEntries(
            @JsonProperty(value = "switch_id") SwitchId switchId,
            @JsonProperty(value = "group_entries") List<GroupEntry> groupEntries) {
        this.switchId = switchId;
        this.groupEntries = groupEntries;
    }
}
