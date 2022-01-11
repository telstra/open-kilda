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

package org.openkilda.rulemanager;

import static java.lang.String.format;

import lombok.Getter;

public enum OfTable {
    INPUT(0),
    PRE_INGRESS(1),
    INGRESS(2),
    POST_INGRESS(3),
    EGRESS(4),
    TRANSIT(5);

    @Getter
    private int tableId;

    OfTable(int tableId) {
        this.tableId = tableId;
    }

    /**
     * Lookup table by table id.
     */
    public static OfTable fromInt(int val) {
        switch (val) {
            case 0:
                return INPUT;
            case 1:
                return PRE_INGRESS;
            case 2:
                return INGRESS;
            case 3:
                return POST_INGRESS;
            case 4:
                return EGRESS;
            case 5:
                return TRANSIT;
            default:
                throw new IllegalArgumentException(format("Unknown table id %d", val));
        }
    }
}
