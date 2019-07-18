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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@JsonNaming(value = SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public class FlowRequest extends CommandData {

    @NonNull
    String flowId;

    @JsonProperty("src_switch")
    SwitchId sourceSwitch;

    @JsonProperty("dst_switch")
    SwitchId destinationSwitch;

    @JsonProperty("src_port")
    int sourcePort;

    @JsonProperty("dst_port")
    int destinationPort;

    @JsonProperty("src_vlan")
    int sourceVlan;

    @JsonProperty("dst_vlan")
    int destinationVlan;

    long bandwidth;
    boolean ignoreBandwidth;
    boolean periodicPings;
    boolean allocateProtectedPath;
    String description;
    int transitEncapsulationId;
    Integer maxLatency;
    Integer priority;
    boolean pinned;
    String diverseFlowId;
    String encapsulationType;
    Type type;

    public enum Type {
        CREATE,
        READ,
        UPDATE,
        DELETE
    }
}
