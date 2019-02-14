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

package org.openkilda.messaging.model.grpc;

public enum PortSpeed {
    PORT_SPEED_RESERVED(0),
    /**
     * <code>PORT_SPEED_10MBFD = 1;</code>
     */
    PORT_SPEED_10MBFD(1),
    /**
     * <code>PORT_SPEED_100MBFD = 2;</code>
     */
    PORT_SPEED_100MBFD(2),
    /**
     * <code>PORT_SPEED_1GBFD = 3;</code>
     */
    PORT_SPEED_1GBFD(3),
    /**
     * <code>PORT_SPEED_10GBFD = 4;</code>
     */
    PORT_SPEED_10GBFD(4),
    /**
     * <code>PORT_SPEED_40GBFD = 5;</code>
     */
    PORT_SPEED_40GBFD(5),
    /**
     * <code>PORT_SPEED_100GBFD = 6;</code>
     */
    PORT_SPEED_100GBFD(6);

    private final int value;

    PortSpeed(int value) {
        this.value = value;
    }

    public int getNumber() {
        return value;
    }
}
