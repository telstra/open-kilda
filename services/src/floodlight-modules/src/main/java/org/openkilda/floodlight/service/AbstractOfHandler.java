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

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.utils.CommandContextFactory;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;

import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public abstract class AbstractOfHandler implements IOFMessageListener {
    private static Logger log = LoggerFactory.getLogger(AbstractOfHandler.class);

    private final CommandContextFactory commandContextFactory;

    public AbstractOfHandler(CommandContextFactory commandContextFactory) {
        this.commandContextFactory = commandContextFactory;
    }

    protected abstract boolean handle(
            CommandContext commandContext, IOFSwitch sw, OFMessage message, FloodlightContext context);

    protected void activateSubscription(IFloodlightProviderService flProviderService, OFType... desiredTypes) {
        for (OFType target : desiredTypes) {
            log.debug("{} activateSubscription for OFMessage with OFType.{}", this.getClass().getName(), target);
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
    @NewCorrelationContextRequired
    public Command receive(IOFSwitch sw, OFMessage message, FloodlightContext context) {
        boolean isHandled;

        final CommandContext commandContext = commandContextFactory.produce();
        final String packetIdentity = String.format(
                "(dpId: %s, xId: %s, version: %s, type: %s)",
                sw.getId(), message.getXid(), message.getVersion(), message.getType());

        log.debug("{} - receive message {}", getClass().getCanonicalName(), packetIdentity);
        try {
            isHandled = handle(commandContext, sw, message, context);
        } catch (Exception e) {
            log.error(String.format("Unhandled exception during processing %s", packetIdentity), e);
            isHandled = false;
        }

        if (isHandled) {
            log.debug("{} - have HANDLED packet {}", getClass().getName(), packetIdentity);
            return Command.STOP;
        }
        log.debug("{} - have NOT HANDLED packet {}", getClass().getName(), packetIdentity);
        return Command.CONTINUE;
    }

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        final boolean isMatch = mustHandleAfter().contains(name);
        log.debug("listener ordering AFTER constraint: {} vs {} - {}", getClass().getName(), name, isMatch);
        return isMatch;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        final boolean isMatch = mustHandleBefore().contains(name);
        log.debug("listener ordering BEFORE constraint: {} vs {} - {}", getClass().getName(), name, isMatch);
        return isMatch;
    }
}
