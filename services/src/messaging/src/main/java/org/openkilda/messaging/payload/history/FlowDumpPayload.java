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

package org.openkilda.messaging.payload.history;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FlowDumpPayload {
    private long bandwidth;

    private boolean ignoreBandwidth;

    private long forwardCookie;

    private long reverseCookie;

    private String sourceSwitch;

    private String destinationSwitch;

    private int sourcePort;

    private int destinationPort;

    private int sourceVlan;

    private int destinationVlan;

    private Long forwardMeterId;

    private Long reverseMeterId;

    private String forwardPath;

    private String reversePath;

    private String forwardStatus;

    private String reverseStatus;
}
