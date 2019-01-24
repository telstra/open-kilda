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

import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "port-number", "assignment", "unique-id", "assignment-type", "pop-location", "inventory-port-uuid",
        "customer", "status", "interface-type", "cross-connect", "notes", "odfmdf", "mmr", "is-active" })
@Data
public class Port {
    @JsonProperty("port-number")
    private Integer portNumber;

    @JsonProperty("assignment")
    private String assignment;
    
    @JsonProperty("assignment-state")
    private String assignmentState;
       
    @JsonProperty("assignment-date")
    private BigInteger assignmentDate;

    @JsonProperty("unique-id")
    private String uuid;

    @JsonProperty("assignment-type")
    private String assignmentType;

    @JsonProperty("pop-location")
    private PopLocation popLocation;

    @JsonProperty("inventory-port-uuid")
    private String inventoryPortUuid;

    @JsonProperty("customer")
    private Customer customer;

    @JsonProperty("status")
    private String status;

    @JsonProperty("interface-type")
    private String interfaceType;

    @JsonProperty("cross-connect")
    private String crossConnect;

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("odfmdf")
    private String odfMdf;

    @JsonProperty("mmr")
    private String mmr;

    @JsonProperty("is-active")
    private String isActive;
}
