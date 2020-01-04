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

package org.openkilda.floodlight.pathverification.type;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.types.OFPort;

public class SwitchPortTuple {
    private IOFSwitch sw;
    private OFPort port;

    public IOFSwitch getSwitch() {
        return sw;
    }

    public SwitchPortTuple setSwitch(IOFSwitch dpid) {
        this.sw = dpid;
        return this;
    }

    public OFPort getPort() {
        return port;
    }

    public SwitchPortTuple setPort(OFPort port) {
        this.port = port;
        return this;
    }
}
