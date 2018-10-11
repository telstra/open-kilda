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

package org.openkilda.floodlight.service.of;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.InputDispatchCommand;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.utils.CommandContextFactory;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;

import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InputService implements IService, IOFMessageListener {
    private static Logger log = LoggerFactory.getLogger(InputService.class);

    private final HashMap<OFType, List<IInputTranslator>> translators = new HashMap<>();
    private final CommandContextFactory commandContextFactory;

    private final Set<String> mustHandleBefore = ImmutableSet.of();
    private final Set<String> mustHandleAfter = ImmutableSet.of();

    private IFloodlightProviderService flProviderService;
    private CommandProcessorService commandProcessor;

    public InputService(CommandContextFactory commandContextFactory) {
        this.commandContextFactory = commandContextFactory;
    }

    /**
     * Service initialize(late) method.
     */
    @Override
    public void setup(FloodlightModuleContext moduleContext) {
        flProviderService = moduleContext.getServiceImpl(IFloodlightProviderService.class);
        commandProcessor = moduleContext.getServiceImpl(CommandProcessorService.class);
    }

    /**
     * Register new OFMessage into Command translator for specifier OFType.
     */
    public void addTranslator(OFType ofType, IInputTranslator inputTranslator) {
        synchronized (this.translators) {
            List<IInputTranslator> queue = translators.merge(
                    ofType, Collections.singletonList(inputTranslator),
                    (stored, toAdd) -> Stream.of(stored, toAdd)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList()));
            if (queue.size() == 1) {
                activateSubscription(flProviderService, ofType);
            }
        }
    }

    @Override
    @NewCorrelationContextRequired
    public Command receive(IOFSwitch sw, OFMessage message, FloodlightContext context) {
        final OfInput input = new OfInput(sw, message, context);  // must be constructed as early as possible
        final String packetIdentity = String.format(
                "(dpId: %s, xId: %s, version: %s, type: %s)",
                sw.getId(), message.getXid(), message.getVersion(), message.getType());

        log.debug("{} - receive message {}", getClass().getCanonicalName(), packetIdentity);
        try {
            handle(input);
        } catch (Exception e) {
            log.error(String.format("Unhandled exception during processing %s", packetIdentity), e);
        }
        return Command.CONTINUE;
    }

    private void handle(OfInput input) {
        final CommandContext commandContext = commandContextFactory.produce();
        List<IInputTranslator> queue = translators.get(input.getType());
        commandProcessor.processLazy(new InputDispatchCommand(commandContext, commandProcessor, queue, input));
    }

    private void activateSubscription(IFloodlightProviderService flProviderService, OFType... desiredTypes) {
        for (OFType target : desiredTypes) {
            log.debug("{} activate subscription for OFMessage with OFType.{}", this.getClass().getName(), target);
            flProviderService.addOFMessageListener(target, this);
        }
    }

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        final boolean isMatch = mustHandleAfter.contains(name);
        log.debug("listener ordering AFTER constraint: {} vs {} - {}", getClass().getName(), name, isMatch);
        return isMatch;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        final boolean isMatch = mustHandleBefore.contains(name);
        log.debug("listener ordering BEFORE constraint: {} vs {} - {}", getClass().getName(), name, isMatch);
        return isMatch;
    }
}
