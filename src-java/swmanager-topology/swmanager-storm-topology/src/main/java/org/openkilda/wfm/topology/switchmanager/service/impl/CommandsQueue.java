/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.model.SwitchId;

import lombok.Getter;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a queue of OpenFlow commands batches.
 */
public class CommandsQueue {

    @Getter
    private final SwitchId switchId;
    @Getter
    private final List<CommandsBatch> commandsBatches;

    private final Iterator<CommandsBatch> iterator;

    public CommandsQueue(SwitchId switchId, List<CommandsBatch> commandsBatches) {
        this.switchId = switchId;
        this.commandsBatches = commandsBatches;
        iterator = commandsBatches.iterator();
    }

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public CommandsBatch getNext() {
        return iterator.next();
    }

    public List<OfCommand> getFirst() {
        return commandsBatches.size() > 0 ? commandsBatches.get(0).getCommands() : Collections.emptyList();
    }
}
