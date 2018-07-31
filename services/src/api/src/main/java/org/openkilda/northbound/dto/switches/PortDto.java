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
public class PortDto {

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty("port_no")
    private int portNo;

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("message")
    private String message;

    public PortDto(@JsonProperty("switch_id") String switchId, @JsonProperty("port_id") int portNo,
                     @JsonProperty("success") Boolean success, @JsonProperty("message") String message) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.success = success;
        this.message = message;
    }
}
