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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public abstract class Command implements Callable<Command> {
    private static final Logger log = LoggerFactory.getLogger(Command.class);

    private final CommandContext context;

    public Command(CommandContext context) {
        this.context = context;
    }

    @Override
    public Command call() {
        Command next;
        try {
            next = execute();
        } catch (Exception e) {
            next = exceptional(e);
        }
        return next;
    }

    protected abstract Command execute() throws Exception;

    protected Command exceptional(Throwable e) {
        log.error(String.format("Unhandled exception into %s: %s", getClass().getName(), e.getMessage()), e);
        return null;
    }

    /**
     * If true - this command will not produce subcommand and can be processed in simplified way by
     * command processor.
     */
    public boolean isOneShot() {
        return true;
    }

    public CommandContext getContext() {
        return context;
    }
}
