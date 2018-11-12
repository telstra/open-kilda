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
@JsonPropertyOrder({ "contractid", "duration", "bandwidth", "price", "contractStatus", "contractStatusName", "version",
        "deletedtimestamp", "currencyCode", "currencyID", "currencyCode", "renewal-option", "contract-start-time",
        "contract-end-time" })
@Data
public class Contract {

    @JsonProperty("contractid")
    private String contractid;

    @JsonProperty("duration")
    private Long duration;

    @JsonProperty("bandwidth")
    private Integer bandwidth;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("contractStatus")
    private String contractStatus;

    @JsonProperty("contractStatusName")
    private String contractStatusName;

    @JsonProperty("version")
    private String version;

    @JsonProperty("deletedtimestamp")
    private Long deletedtimestamp;

    @JsonProperty("currencyCode")
    private String currencyCode;

    @JsonProperty("currencyID")
    private String currencyId;

    @JsonProperty("renewal-option")
    private Boolean renewalOption;

    @JsonProperty("contract-start-time")
    private Long contractStartTime;

    @JsonProperty("contract-end-time")
    private Long contractEndTime;
}
