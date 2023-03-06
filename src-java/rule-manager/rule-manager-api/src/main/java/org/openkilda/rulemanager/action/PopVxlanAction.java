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

package org.openkilda.rulemanager.action;

import static org.openkilda.rulemanager.action.ActionType.POP_VXLAN_NOVIFLOW;
import static org.openkilda.rulemanager.action.ActionType.POP_VXLAN_OVS;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.Value;

import java.util.Set;

@Value
@JsonSerialize
@JsonNaming(SnakeCaseStrategy.class)
public class PopVxlanAction implements Action {
    private static final Set<ActionType> VALID_TYPES = Sets.newHashSet(POP_VXLAN_NOVIFLOW, POP_VXLAN_OVS);

    @Setter(AccessLevel.NONE)
    ActionType type;

    @JsonCreator
    public PopVxlanAction(@JsonProperty("type") ActionType type) {
        if (!VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    String.format("Type %s is invalid. Valid types: %s", type, VALID_TYPES));
        }
        this.type = type;
    }

    @Override
    public ActionType getType() {
        return type;
    }
}
