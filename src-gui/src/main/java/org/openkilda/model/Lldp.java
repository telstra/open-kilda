/* Copyright 2020 Telstra Open Source
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

/**
 * The Class Lldp.
 *
 * @author Swati Sharma
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"mac_address", "ip_address", "chassis_id", "port_id", "ttl", "port_description", "system_name", 
        "system_description", "system_capabilities", "management_address", "time_first_seen", "time_last_seen"})
@Data
public class Lldp {

    @JsonProperty("mac_address")
    private String macAddress;
    
    @JsonProperty("ip_address")
    private String ipAddress;
    
    @JsonProperty("chassis_id")
    private String chassisId;
    
    @JsonProperty("port_id")
    private String portId;
    
    @JsonProperty("ttl")
    private Integer ttl;
    
    @JsonProperty("port_description")
    private String portDescription;
    
    @JsonProperty("system_name")
    private String systemName;
    
    @JsonProperty("system_description")
    private String systemDescription;
    
    @JsonProperty("system_capabilities")
    private String systemCapabilities;
    
    @JsonProperty("management_address")
    private String managementAddress;
    
    @JsonProperty("time_first_seen")
    private String timeFirstSeen;
    
    @JsonProperty("time_last_seen")
    private String timeLastSeen;
}
