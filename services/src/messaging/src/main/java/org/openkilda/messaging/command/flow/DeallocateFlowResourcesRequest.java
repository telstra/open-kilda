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
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class DeallocateFlowResourcesRequest extends CommandData {
    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("unmasked_cookie")
    private long unmaskedCookie;

    @JsonProperty("unmasked_lldp_cookie")
    private Long unmaskedLldpCookie;

    @JsonProperty("path_id")
    private PathId pathId;

    @JsonProperty("encapsulation_type")
    private FlowEncapsulationType encapsulationType;

    public DeallocateFlowResourcesRequest(@JsonProperty("flow_id") String flowId,
                                          @JsonProperty("unmasked_cookie") long unmaskedCookie,
                                          @JsonProperty("unmasked_lldp_cookie") Long unmaskedLldpCookie,
                                          @JsonProperty("path_id") PathId pathId,
                                          @JsonProperty("encapsulation_type") FlowEncapsulationType encapsulationType) {
        this.flowId = flowId;
        this.unmaskedCookie = unmaskedCookie;
        this.unmaskedLldpCookie = unmaskedLldpCookie;
        this.pathId = pathId;
        this.encapsulationType = encapsulationType;
    }
}
