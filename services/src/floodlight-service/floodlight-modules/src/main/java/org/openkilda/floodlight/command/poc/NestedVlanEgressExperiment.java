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

package org.openkilda.floodlight.command.poc;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.SessionProxy;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import java.util.List;

public class NestedVlanEgressExperiment extends NestedVlanExperiment {
    public NestedVlanEgressExperiment(SwitchId switchId, MessageContext messageContext, int inPort, int outPort,
                                      short outerVlan,
                                      short innerVlan, short transitVlan) {
        super(switchId, messageContext, inPort, outPort, outerVlan, innerVlan, transitVlan);
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        return null;
    }
}
