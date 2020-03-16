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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.io.Serializable;

/**
 * The Class SwitchInfo.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SwitchInfo implements Serializable {
    
    private static final long serialVersionUID = 6763064864461521069L;
    
    @JsonProperty("switch_id")
    private String switchId;
    
    @JsonProperty("address")
    private String address;
    
    @JsonProperty("hostname")
    private String hostname;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("common-name")
    private String commonName;

    @JsonProperty("state")
    private String state;

    @JsonProperty("discrepancy")
    private SwitchDiscrepancy discrepancy;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("pop-location")
    private PopLocation popLocation;

    @JsonProperty("model")
    private String model;

    @JsonProperty("rack-location")
    private String rackLocation;

    @JsonProperty("reference-url")
    private String referenceUrl;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("rack-number")
    private String rackNumber;

    @JsonProperty("software-version")
    private String softwareVersion;

    @JsonProperty("manufacturer")
    private String manufacturer;
    
    @JsonProperty("of_version")
    private String ofVersion;
    
    @JsonProperty("hardware")
    private String hardware;
    
    @JsonProperty("software")
    private String software;
    
    @JsonProperty("controller-switch")
    private boolean controllerSwitch;
    
    @JsonProperty("inventory-switch")
    private boolean inventorySwitch;
    
    @JsonProperty("under_maintenance")
    private boolean underMaintenance;
    
    @JsonProperty("evacuate")
    private boolean evacuate;
    
    @JsonProperty("otp")
    private String otp;
}
