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

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Customer implements Serializable {

    private static final long serialVersionUID = 6466554722807017626L;

    @JsonProperty("customer-uuid")
    private String customerUuid;

    @JsonProperty("company-name")
    private String companyName;

    @JsonProperty("customer-account-number")
    private String customerAccountNumber;

    @JsonProperty("customer-type")
    private String customerType;

    @JsonProperty("domain-id")
    private String domainId;

    @JsonProperty("switch-id")
    private String switchId;

    @JsonProperty("port-no")
    private Integer portNumber;

    @JsonProperty("flows")
    private List<PortFlow> portFlows;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Customer) {
            Customer customer = (Customer) obj;
            return customerUuid.equals(customer.customerUuid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((customerUuid == null) ? 0 : customerUuid.hashCode());
        return result;
    }
}
