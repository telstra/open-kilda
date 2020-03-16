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

package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class FlowStatusResponse.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "status"})
public class FlowStatus implements Serializable {

   
    @JsonProperty("flowid")
    private String flowid;

   
    @JsonProperty("status")
    private String status;

    
    private static final long serialVersionUID = -8688238020704231665L;

    /**
     * Gets the flowid.
     *
     * @return the flowid
     */
    
    public String getFlowid() {
        return flowid;
    }

    /**
     * Sets the flowid.
     *
     * @param flowid the new flowid
     */
    
    public void setFlowid(final String flowid) {
        this.flowid = flowid;
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    
    public void setStatus(final String status) {
        this.status = status;
    }

}
