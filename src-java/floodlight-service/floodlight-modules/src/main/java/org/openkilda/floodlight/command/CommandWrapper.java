/* Copyright 2019 Telstra Open Source
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

import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;

/**
 * Wrap {@link Command} execution. Main goal is to setup/clean up environment.
 *
 * <p>One of such setup tasks - is to push correlation-id into MDC.
 */
public class CommandWrapper extends Command {
    private final Command target;

    public CommandWrapper(Command target) {
        super(target.getContext());
        this.target = target;
    }

    @Override
    public Command call() throws Exception {
        Command successor;
        try (CorrelationContextClosable closable = CorrelationContext.create(getContext().getCorrelationId())) {
            successor = target.call();
        }
        return successor;
    }

    @Override
    public Command exceptional(Throwable e) {
        return target.exceptional(e);
    }

    @Override
    public boolean isOneShot() {
        return target.isOneShot();
    }

    public String getName() {
        return target.getName();
    }
}
