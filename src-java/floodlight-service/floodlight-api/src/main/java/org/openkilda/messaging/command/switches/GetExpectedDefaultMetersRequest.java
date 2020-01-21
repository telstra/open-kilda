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

package org.openkilda.messaging.command.switches;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Value;

@Value
@JsonNaming(value = SnakeCaseStrategy.class)
public class GetExpectedDefaultMetersRequest extends CommandData {

    private SwitchId switchId;
    private boolean multiTable;
    private boolean switchLldp;
    private boolean switchArp;

    public GetExpectedDefaultMetersRequest(@JsonProperty("switch_id") SwitchId switchId,
                                           @JsonProperty("multi_table") boolean multiTable,
                                           @JsonProperty("switch_lldp") boolean switchLldp,
                                           @JsonProperty("switch_arp") boolean switchArp) {
        this.switchId = switchId;
        this.multiTable = multiTable;
        this.switchLldp = switchLldp;
        this.switchArp = switchArp;
    }
}
