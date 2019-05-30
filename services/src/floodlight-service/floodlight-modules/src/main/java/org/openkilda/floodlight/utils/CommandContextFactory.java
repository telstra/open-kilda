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

import org.openkilda.floodlight.command.CommandContext;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class CommandContextFactory {
    private FloodlightModuleContext moduleContext = null;

    public void init(FloodlightModuleContext moduleContext) {
        this.moduleContext = moduleContext;
    }

    public CommandContext produce() {
        return new CommandContext(moduleContext);
    }
}
