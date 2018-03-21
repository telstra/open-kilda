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

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;

import java.util.UUID;

/**
 * A proxy for IOFMessageListener which decorates each call with a new correlation context.
 */
public class OFMessageListenerProxyWithCorrelationContext implements IOFMessageListener {

    private final IOFMessageListener ofMessageListener;

    public OFMessageListenerProxyWithCorrelationContext(IOFMessageListener ofMessageListener) {
        this.ofMessageListener = ofMessageListener;
    }

    @Override
    public String getName() {
        return ofMessageListener.getName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType ofType, String s) {
        return ofMessageListener.isCallbackOrderingPrereq(ofType, s);
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType ofType, String s) {
        return ofMessageListener.isCallbackOrderingPostreq(ofType, s);
    }

    @Override
    public Command receive(IOFSwitch iofSwitch, OFMessage ofMessage, FloodlightContext floodlightContext) {
        try (CorrelationContextClosable closable = CorrelationContext.create(UUID.randomUUID().toString())) {
            return ofMessageListener.receive(iofSwitch, ofMessage, floodlightContext);

        }
    }
}