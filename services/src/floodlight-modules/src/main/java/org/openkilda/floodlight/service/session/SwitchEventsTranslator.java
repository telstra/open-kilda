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

package org.openkilda.floodlight.service.session;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

class SwitchEventsTranslator implements IOFSwitchListener {
    private final SessionService service;

    SwitchEventsTranslator(SessionService service, IOFSwitchService iofSwitchService) {
        this.service = service;
        iofSwitchService.addOFSwitchListener(this);
    }

    @Override
    public void switchAdded(DatapathId switchId) {
        service.switchConnect(switchId);
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        service.switchDisconnect(switchId);
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        // do not trace this event
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        // do not trace this event
    }

    @Override
    public void switchChanged(DatapathId switchId) {
        // do not trace this event
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
        // do not trace this event
    }
}
