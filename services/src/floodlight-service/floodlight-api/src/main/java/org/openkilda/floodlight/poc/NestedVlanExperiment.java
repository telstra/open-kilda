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

package org.openkilda.floodlight.poc;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class NestedVlanExperiment extends AbstractMessage {
    @JsonProperty("switch_id")
    private final SwitchId switchId;

    private final short outerVlan;
    private final short innerVlan;

    @JsonCreator
    public NestedVlanExperiment(@JsonProperty("message_context") MessageContext messageContext,
                                @JsonProperty("switch_id") SwitchId switchId,
                                @JsonProperty("outer_vlan") short outerVlan,
                                @JsonProperty("inner_vlan") short innerVlan) {
        super(messageContext);
        this.switchId = switchId;
        this.outerVlan = outerVlan;
        this.innerVlan = innerVlan;
    }
}
