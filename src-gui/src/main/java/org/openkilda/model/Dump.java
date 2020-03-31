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
 * The Class Dump.
 *
 * @author Swati Sharma
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Dump implements Serializable {

    private static final long serialVersionUID = -6793147835649533015L;

    @JsonProperty("type")
    private String type;
    
    @JsonProperty("bandwidth")
    private int bandwidth;
    
    @JsonProperty("ignoreBandwidth")
    private Boolean ignoreBandwidth;

    @JsonProperty("forwardCookie")
    private Long forwardCookie;
    
    @JsonProperty("reverseCookie")
    private Long reverseCookie;
    
    @JsonProperty("sourceSwitch")
    private String sourceSwitch;
    
    @JsonProperty("destinationSwitch")
    private String destinationSwitch;
    
    @JsonProperty("sourcePort")
    private int sourcePort;
    
    @JsonProperty("destinationPort")
    private int destinationPort;
    
    @JsonProperty("sourceVlan")
    private int sourceVlan;
    
    @JsonProperty("destinationVlan")
    private int destinationVlan;
    
    @JsonProperty("forwardMeterId")
    private int forwardMeterId;
    
    @JsonProperty("reverseMeterId")
    private int reverseMeterId;
    
    @JsonProperty("forwardPath")
    private String forwardPath;
    
    @JsonProperty("reversePath")
    private String reversePath;
    
    @JsonProperty("forwardStatus")
    private String forwardStatus;
    
    @JsonProperty("reverseStatus")
    private String reverseStatus;
    
    

}

