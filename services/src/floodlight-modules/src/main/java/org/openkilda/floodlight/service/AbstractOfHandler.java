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

package org.openkilda.floodlight.service;

import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public abstract class AbstractOfHandler implements IOFMessageListener {
    private static Logger log = LoggerFactory.getLogger(AbstractOfHandler.class);

    protected abstract boolean handle(IOFSwitch sw, OFMessage message, FloodlightContext contest);

    protected void activateSubscription(IFloodlightModuleContext moduleContext, OFType... desiredTypes) {
        IFloodlightProviderService flProviderService = moduleContext.getServiceImpl(IFloodlightProviderService.class);

        for (OFType target : desiredTypes) {
            log.debug("activateSubscription {} for OFMessage with OFType.{}", this, target);
            flProviderService.addOFMessageListener(target, this);
        }
    }

    protected Set<String> mustHandleBefore() {
        return ImmutableSet.of();
    }

    protected Set<String> mustHandleAfter() {
        return ImmutableSet.of();
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage message, FloodlightContext context) {
        boolean isHandled;

        log.debug(
                "{} - receive message (xId: {}, type: {})",
                getClass().getCanonicalName(), message.getXid(), message.getType());

        try {
            isHandled = handle(sw, message, context);
        } catch (Exception e) {
            log.error(
                    "Unhandled exception during processing OFMessage(xId: {}, type: {})",
                    message.getXid(), message.getType());
            isHandled = false;
        }

        if (isHandled) {
            return Command.STOP;
        }
        return Command.CONTINUE;
    }

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return mustHandleAfter().contains(name);
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return mustHandleBefore().contains(name);
    }
}
