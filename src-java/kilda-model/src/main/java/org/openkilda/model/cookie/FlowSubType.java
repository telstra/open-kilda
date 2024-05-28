/* Copyright 2023 Telstra Open Source
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

package org.openkilda.model.cookie;

import org.openkilda.model.bitops.NumericEnumField;

import lombok.Getter;

@Getter
public enum FlowSubType implements NumericEnumField {
    // 2 bit long type field

    SHARED(0x00),
    HA_SUB_FLOW_1(0x01),
    HA_SUB_FLOW_2(0x02),

    // This do not consume any value from allowed address space - you can define another field with -1 value.
    // (must be last entry)
    INVALID(-1);

    private final int value;

    FlowSubType(int value) {
        this.value = value;
    }

    public static final FlowSubType[] HA_SUB_FLOW_TYPES = new FlowSubType[] {HA_SUB_FLOW_1, HA_SUB_FLOW_2};
}
