/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.info.event;

import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SwitchInfoExtendedData extends SwitchInfoData {

    @JsonProperty("flows")
    private List<FlowEntry> flows;

    @JsonCreator
    public SwitchInfoExtendedData(
            @JsonProperty("switch_id") final SwitchId switchId,
            @JsonProperty("state") final SwitchState state,
            @JsonProperty("address") final String address,
            @JsonProperty("hostname") final String hostname,
            @JsonProperty("description") final String description,
            @JsonProperty("controller") final String controller,
            @JsonProperty("flows") final List<FlowEntry> flows) {
        super(switchId, state, address, hostname, description, controller);
        this.flows = flows;
    }

    public SwitchInfoExtendedData(final SwitchInfoData sw, final List<FlowEntry> flows) {
        super(sw.getSwitchId(), sw.getState(), sw.getAddress(), sw.getHostname(), sw.getDescription(),
                sw.getController());
        this.flows = flows;
    }
}
