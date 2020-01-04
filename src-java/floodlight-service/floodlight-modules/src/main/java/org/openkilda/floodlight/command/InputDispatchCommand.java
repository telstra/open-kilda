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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.of.IInputTranslator;

import java.util.List;

public class InputDispatchCommand extends Command {
    private final CommandProcessorService commandProcessor;
    private final List<IInputTranslator> subscribers;
    private final OfInput input;

    public InputDispatchCommand(
            CommandContext context, CommandProcessorService commandProcessor,
            List<IInputTranslator> subscribers, OfInput input) {
        super(context);
        this.commandProcessor = commandProcessor;
        this.subscribers = subscribers;
        this.input = input;
    }

    @Override
    public Command call() throws Exception {
        for (IInputTranslator entry : subscribers) {
            Command command = entry.makeCommand(getContext(), input);
            if (command != null) {
                commandProcessor.processLazy(command);
            }
        }

        return null;
    }
}
