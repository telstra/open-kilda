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

import java.io.Serializable;

public enum PortMode implements Serializable {
    PORT_MODE_RESERVED(0),

    PORT_MODE_SGMII(1),

    PORT_MODE_XFI(2);

    private final int value;

    PortMode(int value) {
        this.value = value;
    }

    public int getNumber() {
        return value;
    }
}
