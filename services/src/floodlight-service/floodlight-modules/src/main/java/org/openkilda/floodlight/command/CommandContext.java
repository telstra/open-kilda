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

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

import java.util.UUID;

public class CommandContext {
    private final FloodlightModuleContext moduleContext;
    private final long ctime;
    private String correlationId;

    public CommandContext(FloodlightModuleContext moduleContext) {
        this(moduleContext, UUID.randomUUID().toString());
    }

    public CommandContext(FloodlightModuleContext moduleContext, String correlationId) {
        this.moduleContext = moduleContext;
        this.ctime = System.currentTimeMillis();
        this.correlationId = correlationId;
    }

    public InfoMessage makeInfoMessage(InfoData payload) {
        return new InfoMessage(payload, System.currentTimeMillis(), correlationId);
    }

    public FloodlightModuleContext getModuleContext() {
        return moduleContext;
    }

    public long getCtime() {
        return ctime;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
