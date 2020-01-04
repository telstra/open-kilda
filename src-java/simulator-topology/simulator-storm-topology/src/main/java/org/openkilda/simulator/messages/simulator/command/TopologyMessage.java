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

package org.openkilda.simulator.messages.simulator.command;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.simulator.messages.LinkMessage;
import org.openkilda.simulator.messages.SwitchMessage;
import org.openkilda.simulator.messages.simulator.SimulatorMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "type",
        "switches",
        "links"})

public class TopologyMessage extends SimulatorMessage {
    @JsonProperty("switches")
    private List<SwitchMessage> switches;

    public TopologyMessage(@JsonProperty("switches") final List<SwitchMessage> switches,
                           @JsonProperty("links") final List<LinkMessage> links) {
        this.switches = switches;
    }

    public List<SwitchMessage> getSwitches() {
        return switches;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switches", switches.toString())
                .toString();
    }
}
