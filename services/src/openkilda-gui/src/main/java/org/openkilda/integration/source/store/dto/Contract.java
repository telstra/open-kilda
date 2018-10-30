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

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "contract-id", "duration", "bandwidth", "price", "contract-status", "deleted-timestamp",
        "currency-code", "renewal-option", "contract-start-time", "contract-end-time" })
@Data
public class Contract {

    @JsonProperty("contract-id")
    private String contractid;
    
    @JsonProperty("duration")
    private Long duration;
    
    @JsonProperty("bandwidth")
    private Integer bandwidth;
    
    @JsonProperty("price")
    private BigDecimal price;
    
    @JsonProperty("contract-status")
    private String contractStatus;
    
    @JsonProperty("deleted-timestamp")
    private Long deletedtimestamp;
    
    @JsonProperty("currency-code")
    private String currencyCode;
    
    @JsonProperty("renewal-option")
    private Boolean renewalOption;
    
    @JsonProperty("contract-start-time")
    private Long contractStartTime;
    
    @JsonProperty("contract-end-time")
    private Long contractEndTime;
}
