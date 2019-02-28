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

package org.openkilda.wfm.topology.discovery.storm.bolt.port.command;

import org.openkilda.model.Isl;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.PortHandler;

public class PortSetupCommand extends PortCommand {
    private final PortFacts facts;
    private final Isl history;

    public PortSetupCommand(PortFacts facts, Isl history) {
        super(facts.getEndpoint());
        this.facts = facts;
        this.history = history;
    }

    @Override
    public void apply(PortHandler handler) {
        handler.processSetup(facts, history);
    }
}
