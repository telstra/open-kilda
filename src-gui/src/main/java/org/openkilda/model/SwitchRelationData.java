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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

/**
 * The Class SwitchRelationData.
 *
 * @author Gaurav Chugh
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"switches", "switchrelation", "flows"})
public class SwitchRelationData implements Serializable {

    private static final long serialVersionUID = -2510847461885912823L;
    
    @JsonProperty("switches")
    private List<SwitchInfo> switches = null;

    
    @JsonProperty("switchrelation")
    private List<IslLinkInfo> flows = null;

    
    @JsonProperty("flows")
    private List<FlowInfo> flowResponse = null;

    
    @JsonProperty("ports")
    private List<PortInfo> portInfo = null;

    /**
     * Gets the switches.
     *
     * @return the switches
     */
    
    public List<SwitchInfo> getSwitches() {
        return switches;
    }

    /**
     * Sets the switches.
     *
     * @param switches the new switches
     */
    
    public void setSwitches(final List<SwitchInfo> switches) {
        this.switches = switches;
    }

    /**
     * Gets the switchrelation.
     *
     * @return the switchrelation
     */
    
    public List<IslLinkInfo> getFlows() {
        return flows;
    }

    /**
     * Sets the switchrelation.
     *
     * @param switchrelation the new switchrelation
     */
    
    public void setFlows(final List<IslLinkInfo> switchrelation) {
        flows = switchrelation;
    }

    /**
     * Gets the port info.
     *
     * @return the port info
     */
    
    public List<PortInfo> getPortInfo() {
        return portInfo;
    }

    /**
     * Sets the port info.
     *
     * @param portInfo the new port info
     */
    
    public void setPortInfo(final List<PortInfo> portInfo) {
        this.portInfo = portInfo;
    }

    /**
     * Gets the flow response.
     *
     * @return the flow response
     */
    
    public List<FlowInfo> getFlowResponse() {
        return flowResponse;
    }

    /**
     * Sets the flow response.
     *
     * @param flowResponse the new flow response
     */
    
    public void setFlowResponse(final List<FlowInfo> flowResponse) {
        this.flowResponse = flowResponse;
    }

}
