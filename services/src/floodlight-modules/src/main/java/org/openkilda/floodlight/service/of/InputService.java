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
import org.openkilda.floodlight.service.AbstractOfHandler;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.utils.CommandContextFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InputService extends AbstractOfHandler implements IFloodlightService {
    private final HashMap<OFType, List<IInputTranslator>> translators = new HashMap<>();

    private IFloodlightProviderService flProviderService;
    private CommandProcessorService commandProcessor;

    public InputService(CommandContextFactory commandContextFactory) {
        super(commandContextFactory);
    }

    /**
     * Service init(late) method.
     */
    public void init(FloodlightModuleContext moduleContext) {
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
    protected boolean handle(
            CommandContext commandContext, IOFSwitch sw, OFMessage message, FloodlightContext context) {
        final OfInput input = new OfInput(sw, message, context);  // must be constructed as early as possible

        List<IInputTranslator> queue = translators.get(input.getType());
        commandProcessor.processLazy(new InputDispatchCommand(commandContext, commandProcessor, queue, input));

        return false;
    }
}
