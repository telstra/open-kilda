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

package org.openkilda.northbound.dto.v1.switches;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchDto {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("address")
    private String address;

    @JsonProperty("port")
    private int port;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private SwitchChangeType state;

    @JsonProperty("under_maintenance")
    private boolean underMaintenance;

    @JsonProperty("of_version")
    private String ofVersion;

    @JsonProperty("manufacturer")
    private String manufacturer;

    @JsonProperty("hardware")
    private String hardware;

    @JsonProperty("software")
    private String software;

    @JsonProperty("serial_number")
    private String serialNumber;
}
