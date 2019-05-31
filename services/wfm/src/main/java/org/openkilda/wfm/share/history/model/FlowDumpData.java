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

package org.openkilda.wfm.share.history.model;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
public class FlowDumpData {
    private DumpType dumpType;
    private String flowId;
    private long bandwidth;
    private boolean ignoreBandwidth;
    private SwitchId sourceSwitch;
    private SwitchId destinationSwitch;
    private int sourcePort;
    private int destinationPort;
    private int sourceVlan;
    private int destinationVlan;
    private Cookie forwardCookie;
    private Cookie reverseCookie;
    private MeterId forwardMeterId;
    private MeterId reverseMeterId;
    private String forwardPath;
    private String reversePath;
    private FlowPathStatus forwardStatus;
    private FlowPathStatus reverseStatus;

    @AllArgsConstructor
    @Getter
    public enum DumpType {
        STATE_BEFORE("stateBefore"),
        STATE_AFTER("stateAfter");

        private String type;
    }
}
