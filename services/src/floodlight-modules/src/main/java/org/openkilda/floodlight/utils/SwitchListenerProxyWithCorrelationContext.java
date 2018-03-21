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

package org.openkilda.floodlight.utils;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.UUID;

/**
 * A proxy for IOFSwitchListener which decorates each call with a new correlation context.
 */
public class SwitchListenerProxyWithCorrelationContext implements IOFSwitchListener {

    private final IOFSwitchListener switchListener;

    public SwitchListenerProxyWithCorrelationContext(IOFSwitchListener switchListener) {
        this.switchListener = switchListener;
    }

    @Override
    public void switchAdded(DatapathId datapathId) {
        try (CorrelationContextClosable closable = createNewContext()) {
            switchListener.switchAdded(datapathId);
        }
    }

    @Override
    public void switchRemoved(DatapathId datapathId) {
        try (CorrelationContextClosable closable = createNewContext()) {
            switchListener.switchRemoved(datapathId);
        }
    }

    @Override
    public void switchActivated(DatapathId datapathId) {
        try (CorrelationContextClosable closable = createNewContext()) {
            switchListener.switchActivated(datapathId);
        }

    }

    @Override
    public void switchPortChanged(DatapathId datapathId, OFPortDesc ofPortDesc, PortChangeType portChangeType) {
        try (CorrelationContextClosable closable = createNewContext()) {
            switchListener.switchPortChanged(datapathId, ofPortDesc, portChangeType);
        }
    }

    @Override
    public void switchChanged(DatapathId datapathId) {
        try (CorrelationContextClosable closable = createNewContext()) {
            switchListener.switchChanged(datapathId);
        }
    }

    @Override
    public void switchDeactivated(DatapathId datapathId) {
        try (CorrelationContextClosable closable = createNewContext()) {
            switchListener.switchDeactivated(datapathId);
        }
    }

    private CorrelationContextClosable createNewContext() {
        return CorrelationContext.create(UUID.randomUUID().toString());
    }
}