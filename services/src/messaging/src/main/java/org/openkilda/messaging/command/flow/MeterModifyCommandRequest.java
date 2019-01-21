/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class MeterModifyCommandRequest extends CommandData {

    @JsonProperty("fwd_switch_id")
    private SwitchId fwdSwitchId;

    @JsonProperty("fwd_meter_id")
    private Integer fwdMeterId;

    @JsonProperty("rvs_switch_id")
    private SwitchId rvsSwitchId;

    @JsonProperty("rvs_meter_id")
    private Integer rvsMeterId;

    @JsonProperty("bandwidth")
    private long bandwidth;

    public MeterModifyCommandRequest(
            @JsonProperty("fwd_switch_id") SwitchId fwdSwitchId,
            @JsonProperty("fwd_meter_id") Integer fwdMeterId,
            @JsonProperty("rvs_switch_id") SwitchId rvsSwitchId,
            @JsonProperty("rvs_meter_id") Integer rvsMeterId,
            @JsonProperty("bandwidth") long bandwidth) {
        this.fwdSwitchId = fwdSwitchId;
        this.fwdMeterId = fwdMeterId;
        this.rvsSwitchId = rvsSwitchId;
        this.rvsMeterId = rvsMeterId;
        this.bandwidth = bandwidth;
    }
}
