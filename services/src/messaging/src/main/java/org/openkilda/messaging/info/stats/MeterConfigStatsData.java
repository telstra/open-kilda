/* Copyright 2017 Telstra Open Source
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * This class contains the meter config stats replies for a given switch.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "switch_id",
        "stats"})
public class MeterConfigStatsData extends InfoData {

    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty
    private List<MeterConfigReply> stats;

    public MeterConfigStatsData(@JsonProperty("switch_id") SwitchId switchId,
                                @JsonProperty("stats") List<MeterConfigReply> switchStats) {
        this.switchId = switchId;
        this.stats = switchStats;
    }

    public SwitchId getSwitchId() {
        return switchId;
    }

    public List<MeterConfigReply> getStats() {
        return stats;
    }
}
