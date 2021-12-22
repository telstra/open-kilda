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

import static org.openkilda.rulemanager.action.ActionType.KILDA_SWAP_FIELD;
import static org.openkilda.rulemanager.action.ActionType.NOVI_SWAP_FIELD;

import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;

import java.util.Set;

@Value
@JsonSerialize
public class SwapFieldAction implements Action {

    private static final Set<ActionType> VALID_TYPES = Sets.newHashSet(NOVI_SWAP_FIELD, KILDA_SWAP_FIELD);

    int numberOfBits;
    int srcOffset;
    int dstOffset;
    OpenFlowOxms oxmSrcHeader;
    OpenFlowOxms oxmDstHeader;

    @Setter(AccessLevel.NONE)
    ActionType type;

    @Builder
    public SwapFieldAction(ActionType type,
                           int numberOfBits,
                           int srcOffset,
                           int dstOffset,
                           OpenFlowOxms oxmSrcHeader,
                           OpenFlowOxms oxmDstHeader) {
        if (!VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    String.format("Type %s is invalid. Valid types: %s", type, VALID_TYPES));
        }
        this.type = type;
        this.numberOfBits = numberOfBits;
        this.srcOffset = srcOffset;
        this.dstOffset = dstOffset;
        this.oxmSrcHeader = oxmSrcHeader;
        this.oxmDstHeader = oxmDstHeader;
    }

    @Override
    public ActionType getType() {
        return type;
    }
}
