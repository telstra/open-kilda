/* Copyright 2023 Telstra Open Source
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

package org.openkilda.messaging.info.stats;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"switch_id", "stats"})
@EqualsAndHashCode(callSuper = false)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GroupStatsData extends InfoData {
    private static final long serialVersionUID = 1L;

    SwitchId switchId;
    List<GroupStatsEntry> stats;
}
