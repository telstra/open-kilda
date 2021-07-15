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

package org.openkilda.messaging.command.flow;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ModifyFlowMeterForSwitchManagerRequest extends ModifyDefaultMeterForSwitchManagerRequest {

    @JsonProperty("rate")
    long rate;

    public ModifyFlowMeterForSwitchManagerRequest(@JsonProperty("switch_id") SwitchId switchId,
                                                  @JsonProperty("meter_id") long meterId,
                                                  @JsonProperty("rate") long rate) {
        super(switchId, meterId);
        this.rate = rate;
    }
}
