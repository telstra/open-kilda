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

package org.openkilda.messaging.command.switches;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class ModifyGroupRequest extends CommandData {
    SwitchId switchId;
    MirrorConfig mirrorConfig;
    FlowTransitEncapsulation encapsulation;
    SwitchId egressSwitchId;

    @JsonCreator
    public ModifyGroupRequest(@JsonProperty("switch_id") SwitchId switchId,
                              @JsonProperty("mirror_config") MirrorConfig mirrorConfig,
                              @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation,
                              @JsonProperty("egress_switch_id") SwitchId egressSwitchId) {
        this.switchId = switchId;
        this.mirrorConfig = mirrorConfig;
        this.encapsulation = encapsulation;
        this.egressSwitchId = egressSwitchId;
    }
}
