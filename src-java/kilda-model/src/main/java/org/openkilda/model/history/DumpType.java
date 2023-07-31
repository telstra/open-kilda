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

package org.openkilda.model.history;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DumpType {
    STATE_BEFORE("stateBefore"),
    STATE_AFTER("stateAfter");

    private final String type;

    /**
     * Helper method for creating an enum from a String.
     * @param value String representation
     * @return enum representation
     */
    public static DumpType of(String value) {
        switch (value) {
            case "stateBefore": return STATE_BEFORE;
            case "stateAfter": return STATE_AFTER;
            default:
                throw new IllegalArgumentException("DumpType not supported: " + value);
        }
    }
}
