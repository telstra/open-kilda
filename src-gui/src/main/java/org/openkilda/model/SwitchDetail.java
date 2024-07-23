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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@Data
public class SwitchDetail implements Serializable {

    private static final long serialVersionUID = 6763064864461521069L;
    private String switchId;
    private String name;
    private String address;
    private String port;
    private String hostname;
    private String description;
    private String state;
    private boolean underMaintenance;
    private String ofVersion;
    private String manufacturer;
    private String hardware;
    private String software;
    private String serialNumber;
    private String pop;
    private Location location;
    private InventorySwitch inventorySwitchDetail;

}
