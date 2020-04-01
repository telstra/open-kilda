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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "uuid", "switch-id", "description", "state", "name", "common-name", "pop-location", "model",
        "status", "rack-location", "reference-url", "serial-number", "rack-number", "software-version",
        "manufacturer" })
@Data
public class InventorySwitch implements Serializable {

    private static final long serialVersionUID = 8314830507932457367L;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("switch-id")
    private String switchId;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private String state;

    @JsonProperty("name")
    private String name;

    @JsonProperty("common-name")
    private String commonName;

    @JsonProperty("pop-location")
    private PopLocation popLocation;

    @JsonProperty("model")
    private String model;

    @JsonProperty("status")
    private String status;

    @JsonProperty("rack-location")
    private String rackLocation;

    @JsonProperty("reference-url")
    private String referenceUrl;

    @JsonProperty("serial-number")
    private String serialNumber;

    @JsonProperty("rack-number")
    private String rackNumber;

    @JsonProperty("software-version")
    private String softwareVersion;

    @JsonProperty("manufacturer")
    private String manufacturer;

}
