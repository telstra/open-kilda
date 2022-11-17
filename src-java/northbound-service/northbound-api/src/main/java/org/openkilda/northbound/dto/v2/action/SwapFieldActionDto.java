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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@JsonNaming(SnakeCaseStrategy.class)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class SwapFieldActionDto extends BaseAction {

    int numberOfBits;
    int srcOffset;
    int dstOffset;
    String srcHeader;
    String dstHeader;

    @JsonCreator
    public SwapFieldActionDto(@JsonProperty("action_type") @NonNull String actionType,
                              @JsonProperty("number_of_bits") int numberOfBits,
                              @JsonProperty("src_offset") int srcOffset,
                              @JsonProperty("dst_offset") int dstOffset,
                              @JsonProperty("src_header") String srcHeader,
                              @JsonProperty("dst_header") String dstHeader) {
        super(actionType);
        this.numberOfBits = numberOfBits;
        this.srcOffset = srcOffset;
        this.dstOffset = dstOffset;
        this.srcHeader = srcHeader;
        this.dstHeader = dstHeader;
    }
}
