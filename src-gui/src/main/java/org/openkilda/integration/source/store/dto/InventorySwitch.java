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

package org.openkilda.integration.source.store.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"uuid", "switch-id", "description", "name", "common-name", "pop-location", "model",
        "status", "rack-location", "reference-url", "serial-number", "rack-number", "software-version",
        "manufacturer"})
@Data
public class InventorySwitch implements Serializable {

    private static final long serialVersionUID = 8314830507932457367L;

    private String uuid;
    private String switchId;
    private String description;
    private String name;
    private String commonName;
    private PopLocation popLocation;
    private String model;
    private String status;
    private String rackLocation;
    private String referenceUrl;
    private String serialNumber;
    private String rackNumber;
    private String softwareVersion;
    private String manufacturer;
    private boolean hasDuplicate; // the value for this field is dynamically generated
}
