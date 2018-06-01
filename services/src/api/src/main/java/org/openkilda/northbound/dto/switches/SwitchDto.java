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

package org.openkilda.northbound.dto.switches;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class SwitchDto {

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty("address")
    private String address;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private String state;

    public SwitchDto(@JsonProperty("switch_id") String switchId, @JsonProperty("address") String address,
                     @JsonProperty("hostname") String hostname, @JsonProperty("description") String description,
                     @JsonProperty("state") String state) {
        this.switchId = switchId;
        this.address = address;
        this.hostname = hostname;
        this.description = description;
        this.state = state;
    }
}
