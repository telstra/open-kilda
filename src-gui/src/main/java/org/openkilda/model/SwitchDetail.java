/* Copyright 2024 Telstra Open Source
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

package org.openkilda.model;

import org.openkilda.integration.source.store.dto.InventorySwitch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@Data
public class SwitchDetail implements Serializable {

    private static final long serialVersionUID = 6763064864461521069L;
    @JsonProperty("switch_id")
    private String switchId;
    @JsonProperty("name")
    private String name;
    @JsonProperty("address")
    private String address;
    @JsonProperty("port")
    private String port;
    @JsonProperty("hostname")
    private String hostname;
    @JsonProperty("description")
    private String description;
    @JsonProperty("state")
    private String state;
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
    @JsonProperty("pop")
    private String pop;
    @JsonProperty("location")
    private Location location;
    @JsonProperty("inventory_switch_detail")
    private InventorySwitch inventorySwitchDetail;

}
