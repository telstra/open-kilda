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

import java.util.concurrent.Future;

public class ResolveFutureCommand<T> extends Command {
    private static final Logger log = LoggerFactory.getLogger(ResolveFutureCommand.class);

    private final Future<T> future;
    private final Command nextCommand;

    public ResolveFutureCommand(CommandContext context, Future<T> future, Command nextCommand) {
        super(context);
        this.future = future;
        this.nextCommand = nextCommand;
    }

    @Override
    public Command call() throws Exception {
        Command schedule = this;

        log.debug("Check for future completion, consumer {}", nextCommand);
        if (future.isDone()) {
            log.debug("Future is resolved, consumer {}", nextCommand);
            schedule = nextCommand;
        }

        return schedule;
    }
}
