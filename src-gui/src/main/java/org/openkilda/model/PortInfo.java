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

import org.openkilda.integration.source.store.dto.Customer;
import org.openkilda.integration.source.store.dto.PopLocation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

/**
 * The Class PortInfo.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class PortInfo implements Serializable, Comparable<PortInfo> {

    private static final long serialVersionUID = 6234209548424333879L;
    
    @JsonProperty("assignmenttype")
    private String assignmenttype;
    
    @JsonProperty("assignment-state")
    private String assignmentState;
       
    @JsonProperty("assignment-date")
    private BigInteger assignmentDate;

    @JsonProperty("interfacetype")
    private String interfacetype;

    @JsonProperty("status")
    private String status;
    
    @JsonProperty("crossconnect")
    private String crossconnect;
    
    @JsonProperty("customeruuid")
    private String customeruuid;

    @JsonProperty("switch_id")
    private String switchName;
    
    @JsonProperty("port_name")
    private String portName;

    @JsonProperty("port_number")
    private String portNumber;
    
    @JsonProperty("stats")
    private Map<String, Double> stats;

    @JsonProperty("unique-id")
    private String uuid;

    @JsonProperty("pop-location")
    private PopLocation popLocation;

    @JsonProperty("inventory-port-uuid")
    private String inventoryPortUuid;

    @JsonProperty("customer")
    private Customer customer;

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("odfmdf")
    private String odfMdf;

    @JsonProperty("mmr")
    private String mmr;

    @JsonProperty("is-active")
    private String isActive;
    
    @JsonProperty("discrepancy")
    private PortDiscrepancy discrepancy;

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final PortInfo port) {
        Integer portNumber1 = Integer.parseInt(portNumber);
        Integer portNumber2 = Integer.parseInt(port.portNumber);
        return portNumber1 - portNumber2;
    }

}
