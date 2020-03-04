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

package org.openkilda.floodlight.switchmanager.factory;

import lombok.Builder;
import lombok.Data;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFMeterMod;

@Data
@Builder
public class SwitchFlowTuple {

    public static final SwitchFlowTuple EMPTY = SwitchFlowTuple.builder().build();

    private IOFSwitch sw;
    private OFFlowMod flow;
    private OFMeterMod meter;
    private OFGroupAdd group;
}
