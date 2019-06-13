/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.command;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

public final class Constants {
    public static final String flowName = "test_flow";
    public static final SwitchId switchId = new SwitchId("00:00:00:00:00:00:00:01");
    public static final int inputPort = 1;
    public static final int outputPort = 2;
    public static final int transitEncapsulationId = 100;
    public static final FlowEncapsulationType transitEncapsulationType = FlowEncapsulationType.TRANSIT_VLAN;
    public static final int outputVlanId = 200;
    public static final int inputVlanId = 300;
    public static final long bandwidth = 10000;
    public static final long meterId = 1;
    public static final long burstSize = 1024;
    public static final OutputVlanType outputVlanType = OutputVlanType.REPLACE;

    private Constants() {
        throw new UnsupportedOperationException();
    }
}
