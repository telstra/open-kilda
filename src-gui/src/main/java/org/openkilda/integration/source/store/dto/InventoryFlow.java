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

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "flow-id", "description", "customer-name", "source", "destination", "maximum-bandwidth",
        "ignore-bandwidth", "status" })
@Data
public class InventoryFlow {

    @JsonProperty("flow-id")
    private String id;

    @JsonProperty("description")
    private String description;

    @JsonProperty("customer-name")
    private String customerName;

    @JsonProperty("source")
    private Switch source;

    @JsonProperty("destination")
    private Switch destination;

    @JsonProperty("maximum-bandwidth")
    private Integer maximumBandwidth;

    @JsonProperty("ignore-bandwidth")
    private Boolean ignoreBandwidth;

    @JsonProperty("status")
    private String state;

}
