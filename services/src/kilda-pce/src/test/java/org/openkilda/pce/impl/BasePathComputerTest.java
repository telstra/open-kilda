/* Copyright 2018 Telstra Open Source
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

package org.openkilda.pce.impl;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

public abstract class BasePathComputerTest {
    protected static final int ALLOWED_DEPTH = 35;
    protected static final int DEFAULT_COST = 700;

    protected void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                           int cost, int latency) {
        Switch srcSwitch = Switch.builder().switchId(srcDpid).build();
        Switch dstSwitch = Switch.builder().switchId(dstDpid).build();

        Isl isl = Isl.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .srcPort(srcPort)
                .destPort(dstPort)
                .cost(cost)
                .latency(latency)
                .build();
        network.addLink(isl);
    }
}
