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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * The Class FlowResponse.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "source_switch", "src_port", "src_vlan", "target_switch", "dst_port", "dst_vlan",
        "maximum_bandwidth", "status", "description", "diverse-flowid", "last-updated", "discrepancy"})
@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
public class FlowInfo implements Serializable {
    private static final long serialVersionUID = -7015976328478701934L;

    @JsonProperty("flowid")
    private String flowid;
    @JsonProperty("y_flow_id")
    private String yFlowId;
    private String sourceSwitch;
    private int srcPort;
    private int srcVlan;
    private int srcInnerVlan;
    private String targetSwitchName;
    private String sourceSwitchName;
    private String targetSwitch;
    private int dstPort;
    private int dstVlan;
    private int dstInnerVlan;
    @JsonProperty("diverse-flowid")
    private String diverseFlowid;
    private int maximumBandwidth;
    private boolean allocateProtectedPath;
    private String status;
    private String description;
    @JsonProperty("last-updated")
    private String lastUpdated;
    private FlowDiscrepancy discrepancy;
    private boolean ignoreBandwidth;
    private String state;
    @JsonProperty("controller-flow")
    private boolean controllerFlow;
    @JsonProperty("inventory-flow")
    private boolean inventoryFlow;
    private boolean pinned;
    @JsonProperty("encapsulation-type")
    private String encapsulationType;
    @JsonProperty("path-computation-strategy")
    private String pathComputationStrategy;
    @JsonProperty("periodic-pings")
    private boolean periodicPings;
    private String created;
    private boolean srcLldp;
    private boolean srcArp;
    private boolean dstLldp;
    private boolean dstArp;
    private List<String> diverseWith;
    @JsonProperty("max-latency")
    private Long maxLatency;
    @JsonProperty("max-latency-tier2")
    private Long maxLatencyTier2;
    private int priority;
    private String statusInfo;
    @JsonProperty("target-path-computation_strategy")
    private String targetPathComputationStrategy;
    @JsonProperty("status-details")
    private StatusDetail statusDetails;
}
