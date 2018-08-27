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

/**
 * The Class FlowResponse.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "source_switch", "src_port", "src_vlan", "target_switch", "dst_port",
        "dst_vlan", "maximum_bandwidth", "status", "description", "last-updated"})
public class FlowInfo implements Serializable {

    @JsonProperty("flowid")
    private String flowid;


    @JsonProperty("source_switch")
    private String sourceSwitch;


    @JsonProperty("src_port")
    private int srcPort;


    @JsonProperty("src_vlan")
    private int srcVlan;


    @JsonProperty("target_switch_name")
    private String targetSwitchName;

    @JsonProperty("source_switch_name")
    private String sourceSwitchName;

    @JsonProperty("target_switch")
    private String targetSwitch;


    @JsonProperty("dst_port")
    private int dstPort;


    @JsonProperty("dst_vlan")
    private int dstVlan;


    @JsonProperty("maximum_bandwidth")
    private int maximumBandwidth;


    @JsonProperty("status")
    private String status;


    @JsonProperty("description")
    private String description;


    @JsonProperty("last-updated")
    private String lastUpdated;


    private static final long serialVersionUID = -7015976328478701934L;

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
     * Gets the source switch.
     *
     * @return the source switch
     */

    public String getSourceSwitch() {
        return sourceSwitch;
    }

    /**
     * Sets the source switch.
     *
     * @param sourceSwitch the new source switch
     */

    public void setSourceSwitch(final String sourceSwitch) {
        this.sourceSwitch = sourceSwitch;
    }

    /**
     * Gets the src port.
     *
     * @return the src port
     */

    public int getSrcPort() {
        return srcPort;
    }

    /**
     * Sets the src port.
     *
     * @param srcPort the new src port
     */

    public void setSrcPort(final int srcPort) {
        this.srcPort = srcPort;
    }

    /**
     * Gets the src vlan.
     *
     * @return the src vlan
     */

    public int getSrcVlan() {
        return srcVlan;
    }

    /**
     * Sets the src vlan.
     *
     * @param srcVlan the new src vlan
     */

    public void setSrcVlan(final int srcVlan) {
        this.srcVlan = srcVlan;
    }

    /**
     * Gets the target switch.
     *
     * @return the target switch
     */

    public String getTargetSwitch() {
        return targetSwitch;
    }

    /**
     * Sets the target switch.
     *
     * @param targetSwitch the new target switch
     */

    public void setTargetSwitch(final String targetSwitch) {
        this.targetSwitch = targetSwitch;
    }

    /**
     * Gets the dst port.
     *
     * @return the dst port
     */

    public int getDstPort() {
        return dstPort;
    }

    /**
     * Sets the dst port.
     *
     * @param dstPort the new dst port
     */

    public void setDstPort(final int dstPort) {
        this.dstPort = dstPort;
    }

    /**
     * Gets the dst vlan.
     *
     * @return the dst vlan
     */

    public int getDstVlan() {
        return dstVlan;
    }

    /**
     * Sets the dst vlan.
     *
     * @param dstVlan the new dst vlan
     */

    public void setDstVlan(final int dstVlan) {
        this.dstVlan = dstVlan;
    }

    /**
     * Gets the maximum bandwidth.
     *
     * @return the maximum bandwidth
     */

    public int getMaximumBandwidth() {
        return maximumBandwidth;
    }

    /**
     * Sets the maximum bandwidth.
     *
     * @param maximumBandwidth the new maximum bandwidth
     */

    public void setMaximumBandwidth(final int maximumBandwidth) {
        this.maximumBandwidth = maximumBandwidth;
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

    /**
     * Gets the description.
     *
     * @return the description
     */

    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */

    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Gets the last updated.
     *
     * @return the last updated
     */

    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets the last updated.
     *
     * @param lastUpdated the new last updated
     */

    public void setLastUpdated(final String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getTargetSwitchName() {
        return targetSwitchName;
    }

    public void setTargetSwitchName(String targetSwitchName) {
        this.targetSwitchName = targetSwitchName;
    }

    public String getSourceSwitchName() {
        return sourceSwitchName;
    }

    public void setSourceSwitchName(String sourceSwitchName) {
        this.sourceSwitchName = sourceSwitchName;
    }

}
