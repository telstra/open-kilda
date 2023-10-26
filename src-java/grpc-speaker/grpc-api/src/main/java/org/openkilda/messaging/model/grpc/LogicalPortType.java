/* Copyright 2020 Telstra Open Source
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

package org.openkilda.messaging.model.grpc;

import java.io.Serializable;

public enum LogicalPortType implements Serializable {
    LAG,
    BFD,
    RESERVED;

    /**
     * Create enum by number.
     *
     * @param value an int value.
     * @return the enum value
     */
    public static LogicalPortType forNumber(int value) {
        switch (value) {
            case 1:
                return LAG;
            case 2:
                return BFD;
            default:
                throw new IllegalArgumentException("Invalid logical port type");
        }
    }
}
