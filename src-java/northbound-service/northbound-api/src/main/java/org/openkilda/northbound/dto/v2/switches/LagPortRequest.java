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

package org.openkilda.northbound.dto.v2.switches;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class LagPortRequest {
    @JsonProperty("port_numbers")
    private Set<Integer> portNumbers;

    @JsonProperty("lacp_reply")
    private Boolean lacpReply;

    @Builder
    @JsonCreator
    public LagPortRequest(
            @JsonProperty("port_numbers") Set<Integer> portNumbers,
            @JsonProperty("lacp_reply") Boolean lacpReply) {
        this.portNumbers = portNumbers;
        setLacpReply(lacpReply);
    }

    public void setLacpReply(Boolean lacpReply) {
        this.lacpReply = lacpReply == null ? true : lacpReply;
    }
}
