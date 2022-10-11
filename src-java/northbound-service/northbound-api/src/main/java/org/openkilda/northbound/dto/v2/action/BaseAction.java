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
@JsonSubTypes(
        {
                @Type(value = CopyFieldActionDto.class, name = "CopyFieldActionDto"),
                @Type(value = GroupActionDto.class, name = "GroupActionDto"),
                @Type(value = MeterActionDto.class, name = "MeterActionDto"),
                @Type(value = PopVlanActionDto.class, name = "PopVlanActionDto"),
                @Type(value = PopVxlanActionDto.class, name = "PopVxlanActionDto"),
                @Type(value = PortOutActionDto.class, name = "PortOutActionDto"),
                @Type(value = PushVlanActionDto.class, name = "PushVlanActionDto"),
                @Type(value = PushVxlanActionDto.class, name = "PushVxlanActionDto"),
                @Type(value = SetFieldActionDto.class, name = "SetFieldActionDto"),
                @Type(value = SwapFieldActionDto.class, name = "SwapFieldActionDto")
        })
public class BaseAction {
    @NonNull
    protected String actionType;
}
