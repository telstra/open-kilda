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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;

/**
 * Represents information about a metadata.
 * Uses 64 bit to encode information about the flow:
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |            Encapsulation ID           |D|                     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Reserved Prefix                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * D - flag indicates direction.
 * </p>
 */
@Value
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class Metadata implements Serializable {
    private static final long serialVersionUID = 487013596500006404L;

    private static final long ENCAPSULATION_ID_MASK = 0x0000_0000_000F_FFFFL;
    private static final long FORWARD_METADATA_FLAG = 0x0000_0000_0010_0000L;

    private final long encapsulationId;
    private final boolean forward;

    @Builder
    public Metadata(@JsonProperty("encapsulation_id") long encapsulationId,
                    @JsonProperty("forward") boolean forward) {
        this.encapsulationId = encapsulationId;
        this.forward = forward;

    }

    public Metadata(long rawValue) {
        this.encapsulationId = rawValue & ENCAPSULATION_ID_MASK;
        this.forward = (rawValue & FORWARD_METADATA_FLAG) == FORWARD_METADATA_FLAG;
    }

    @JsonIgnore
    public long getRawValue() {
        long directionFlag = forward ? FORWARD_METADATA_FLAG : 0L;
        return (encapsulationId & ENCAPSULATION_ID_MASK) | directionFlag;
    }

    public static String toString(long metadata) {
        return String.format("0x%016X", metadata);
    }
}
