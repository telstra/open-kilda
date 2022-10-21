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

package org.openkilda.northbound.dto.v2.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@Value
@NonFinal
@JsonNaming(SnakeCaseStrategy.class)
@SuperBuilder
@JsonTypeInfo(use = Id.NAME, visible = true, property = "action_type")
@JsonSubTypes({
        @Type(value = CopyFieldActionDto.class, name = "NOVI_COPY_FIELD"),
        @Type(value = GroupActionDto.class, name = "GROUP"),
        @Type(value = MeterActionDto.class, name = "METER"),
        @Type(value = PopVlanActionDto.class, name = "POP_VLAN"),
        @Type(value = PopVxlanActionDto.class, name = "POP_VXLAN_NOVIFLOW"),
        @Type(value = PopVxlanActionDto.class, name = "POP_VXLAN_OVS"),
        @Type(value = PortOutActionDto.class, name = "PORT_OUT"),
        @Type(value = PushVlanActionDto.class, name = "PUSH_VLAN"),
        @Type(value = PushVxlanActionDto.class, name = "PUSH_VXLAN_NOVIFLOW"),
        @Type(value = PushVxlanActionDto.class, name = "PUSH_VXLAN_OVS"),
        @Type(value = SetFieldActionDto.class, name = "SET_FIELD"),
        @Type(value = SwapFieldActionDto.class, name = "NOVI_SWAP_FIELD"),
        @Type(value = SwapFieldActionDto.class, name = "KILDA_SWAP_FIELD")
})
public class BaseAction {
    @NonNull
    protected String actionType;

    protected BaseAction(@NonNull String actionType) {
        this.actionType = actionType;
    }
}
