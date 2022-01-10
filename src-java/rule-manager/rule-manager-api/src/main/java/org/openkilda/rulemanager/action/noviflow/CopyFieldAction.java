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

package org.openkilda.rulemanager.action.noviflow;

import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Value;

@Value
@JsonSerialize
@Builder
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(value = { "type" })
public class CopyFieldAction implements Action {

    int numberOfBits;
    int srcOffset;
    int dstOffset;
    OpenFlowOxms oxmSrcHeader;
    OpenFlowOxms oxmDstHeader;

    @JsonCreator
    public CopyFieldAction(@JsonProperty("number_of_bits") int numberOfBits,
                           @JsonProperty("src_offset") int srcOffset,
                           @JsonProperty("dst_offset") int dstOffset,
                           @JsonProperty("oxm_src_header") OpenFlowOxms oxmSrcHeader,
                           @JsonProperty("oxm_dst_header") OpenFlowOxms oxmDstHeader) {
        this.numberOfBits = numberOfBits;
        this.srcOffset = srcOffset;
        this.dstOffset = dstOffset;
        this.oxmSrcHeader = oxmSrcHeader;
        this.oxmDstHeader = oxmDstHeader;
    }

    @Override
    public ActionType getType() {
        return ActionType.NOVI_COPY_FIELD;
    }
}
