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

package org.openkilda.messaging.info.stats;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Set;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
public class SwitchPortStatusData extends InfoData {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("ports")
    private Set<PortStatusData> ports;

    @JsonProperty("requester")
    private String requester;


    @JsonCreator
    public SwitchPortStatusData(
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("ports") Set<PortStatusData> ports,
            @JsonProperty("requester") String requester) {
        this.switchId = switchId;
        this.ports = ports;
        this.requester = requester;
    }
}
