/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.model;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowStatusDetails;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(exclude = {"ignoreBandwidth", "periodicPings", "cookie", "createdTime", "lastUpdated", "meterId",
        "transitEncapsulationId"})
public class FlowDto implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    private static final long MASK_COOKIE_FLAGS = 0x0000_0000_FFFF_FFFFL;

    /**
     * Flow id.
     */
    @JsonProperty(Utils.FLOW_ID)
    private String flowId;

    /**
     * FLow bandwidth.
     */
    @JsonProperty("bandwidth")
    private long bandwidth;

    /**
     * Should flow ignore bandwidth in path computation.
     */
    @JsonProperty("ignore_bandwidth")
    private boolean ignoreBandwidth;

    @JsonProperty("strict_bandwidth")
    private boolean strictBandwidth;

    @JsonProperty("periodic-pings")
    private Boolean periodicPings;

    @JsonProperty("allocate_protected_path")
    private boolean allocateProtectedPath;

    /**
     * Flow cookie.
     */
    @JsonProperty("cookie")
    private long cookie;

    /**
     * Flow description.
     */
    @JsonProperty("description")
    private String description;

    @JsonProperty("created_time")
    private String createdTime;

    /**
     * Flow last updated timestamp.
     */
    @JsonProperty("last_updated")
    private String lastUpdated;

    /**
     * Flow source switch.
     */
    @JsonProperty("src_switch")
    private SwitchId sourceSwitch;

    /**
     * Flow destination switch.
     */
    @JsonProperty("dst_switch")
    private SwitchId destinationSwitch;

    /**
     * Flow source port.
     */
    @JsonProperty("src_port")
    private int sourcePort;

    /**
     * Flow destination port.
     */
    @JsonProperty("dst_port")
    private int destinationPort;

    /**
     * Flow source vlan id.
     */
    @JsonProperty("src_vlan")
    private int sourceVlan;

    /**
     * Flow destination vlan id.
     */
    @JsonProperty("dst_vlan")
    private int destinationVlan;

    @JsonProperty("src_inner_vlan")
    private int sourceInnerVlan;

    @JsonProperty("dst_inner_vlan")
    private int destinationInnerVlan;

    @JsonProperty("detect_connected_devices")
    private DetectConnectedDevicesDto detectConnectedDevices
            = new DetectConnectedDevicesDto();

    /**
     * Flow source meter id.
     */
    @JsonProperty("meter_id")
    private Integer meterId;

    /**
     * Flow transit encapsulation id.
     */
    @JsonProperty("transit_encapsulation_id")
    private int transitEncapsulationId;

    /**
     * Flow state.
     */
    @JsonProperty("state")
    private FlowState state;

    @JsonProperty("status_details")
    private FlowStatusDetails flowStatusDetails;

    @JsonProperty("status_info")
    private String statusInfo;

    @JsonProperty("max_latency")
    private Long maxLatency;

    @JsonProperty("max_latency_tier2")
    private Long maxLatencyTier2;

    @JsonProperty("priority")
    private Integer priority;

    @JsonProperty("pinned")
    private boolean pinned;

    @JsonProperty("encapsulation_type")
    private FlowEncapsulationType encapsulationType;

    @JsonProperty("path_computation_strategy")
    private PathComputationStrategy pathComputationStrategy;

    @JsonProperty("target_path_computation_strategy")
    private PathComputationStrategy targetPathComputationStrategy;

    @JsonProperty("diverse_with")
    private Set<String> diverseWith;

    @JsonProperty("diverse_with_y_flows")
    private Set<String> diverseWithYFlows;

    @JsonProperty("diverse_with_ha_flows")
    private Set<String> diverseWithHaFlows;

    @JsonProperty("affinity_with")
    private String affinityWith;

    @JsonProperty("loop_switch_id")
    private SwitchId loopSwitchId;

    @JsonProperty("mirror_point_statuses")
    private List<MirrorPointStatusDto> mirrorPointStatuses;

    @JsonProperty("forward_latency")
    private Long forwardLatency;

    @JsonProperty("reverse_latency")
    private Long reverseLatency;

    @JsonProperty("latency_last_modified_time")
    private Instant latencyLastModifiedTime;

    @JsonProperty("y_flow_id")
    @JsonInclude(Include.NON_NULL)
    private String yFlowId;

    @JsonProperty("vlan_statistics")
    private Set<Integer> vlanStatistics;

    public FlowDto() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId                    flow id
     * @param bandwidth                 bandwidth
     * @param ignoreBandwidth           ignore bandwidth flag
     * @param strictBandwidth           strict bandwidth flag
     * @param periodicPings             enable periodic pings
     * @param allocateProtectedPath     allocate protected flow path.
     * @param cookie                    cookie
     * @param description               description
     * @param createdTime               flow created timestamp
     * @param lastUpdated               last updated timestamp
     * @param sourceSwitch              source switch
     * @param destinationSwitch         destination switch
     * @param sourcePort                source port
     * @param destinationPort           destination port
     * @param sourceVlan                source vlan id
     * @param destinationVlan           destination vlan id
     * @param meterId                   meter id
     * @param transitEncapsulationId    transit vlan id
     * @param state                     flow state
     * @param flowStatusDetails         flow status details
     * @param statusInfo                flow status info
     * @param maxLatency                max latency
     * @param priority                  flow priority
     * @param pinned                    pinned flag
     * @param encapsulationType         flow encapsulation type
     * @param detectConnectedDevices    detectConnectedDevices flags
     * @param pathComputationStrategy   path computation strategy
     * @param targetPathComputationStrategy   target path computation strategy
     * @param diverseWith               flow ids diverse with
     * @param affinityWith              flow id affinity with
     * @param loopSwitchId              loop switch id
     * @param mirrorPointStatuses       mirror path statuses
     * @param forwardLatency            forward path latency nanoseconds
     * @param reverseLatency            reverse path latency nanoseconds
     * @param latencyLastModifiedTime   latency fields last modified time
     * @param yFlowId                   the y-flow ID in the case of sub-flow
     * @param vlanStatistics            flow vlan statistics
     */
    @JsonCreator
    @Builder(toBuilder = true)
    public FlowDto(@JsonProperty(Utils.FLOW_ID) final String flowId,
                   @JsonProperty("bandwidth") final long bandwidth,
                   @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
                   @JsonProperty("strict_bandwidth") boolean strictBandwidth,
                   @JsonProperty("periodic-pings") Boolean periodicPings,
                   @JsonProperty("allocate_protected_path") boolean allocateProtectedPath,
                   @JsonProperty("cookie") final long cookie,
                   @JsonProperty("description") final String description,
                   @JsonProperty("created_time") String createdTime,
                   @JsonProperty("last_updated") final String lastUpdated,
                   @JsonProperty("src_switch") final SwitchId sourceSwitch,
                   @JsonProperty("dst_switch") final SwitchId destinationSwitch,
                   @JsonProperty("src_port") final int sourcePort,
                   @JsonProperty("dst_port") final int destinationPort,
                   @JsonProperty("src_vlan") final int sourceVlan,
                   @JsonProperty("dst_vlan") final int destinationVlan,
                   @JsonProperty("src_inner_vlan") final int sourceInnerVlan,
                   @JsonProperty("dst_inner_vlan") final int destinationInnerVlan,
                   @JsonProperty("meter_id") final Integer meterId,
                   @JsonProperty("transit_encapsulation_id") final int transitEncapsulationId,
                   @JsonProperty("state") FlowState state,
                   @JsonProperty("status_details") FlowStatusDetails flowStatusDetails,
                   @JsonProperty("status_info") String statusInfo,
                   @JsonProperty("max_latency") Long maxLatency,
                   @JsonProperty("max_latency_tier2") Long maxLatencyTier2,
                   @JsonProperty("priority") Integer priority,
                   @JsonProperty("pinned") boolean pinned,
                   @JsonProperty("encapsulation_type") FlowEncapsulationType encapsulationType,
                   @JsonProperty("detect_connected_devices") DetectConnectedDevicesDto detectConnectedDevices,
                   @JsonProperty("path_computation_strategy") PathComputationStrategy pathComputationStrategy,
                   @JsonProperty("target_path_computation_strategy")
                               PathComputationStrategy targetPathComputationStrategy,
                   @JsonProperty("diverse_with") Set<String> diverseWith,
                   @JsonProperty("diverse_with_y_flows") Set<String> diverseWithYFlows,
                   @JsonProperty("affinity_with") String affinityWith,
                   @JsonProperty("loop_switch_id") SwitchId loopSwitchId,
                   @JsonProperty("mirror_point_statuses") List<MirrorPointStatusDto> mirrorPointStatuses,
                   @JsonProperty("forward_latency") Long forwardLatency,
                   @JsonProperty("reverse_latency") Long reverseLatency,
                   @JsonProperty("latency_last_modified_time") Instant latencyLastModifiedTime,
                   @JsonProperty("y_flow_id") @JsonInclude(Include.NON_NULL) String yFlowId,
                   @JsonProperty("vlan_statistics") Set<Integer> vlanStatistics) {
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.strictBandwidth = strictBandwidth;
        this.periodicPings = periodicPings;
        this.allocateProtectedPath = allocateProtectedPath;
        this.cookie = cookie;
        this.description = description;
        this.createdTime = createdTime;
        this.lastUpdated = lastUpdated;
        this.sourceSwitch = sourceSwitch;
        this.destinationSwitch = destinationSwitch;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sourceVlan = sourceVlan;
        this.destinationVlan = destinationVlan;
        this.sourceInnerVlan = sourceInnerVlan;
        this.destinationInnerVlan = destinationInnerVlan;
        this.transitEncapsulationId = transitEncapsulationId;
        this.meterId = meterId;
        this.state = state;
        this.flowStatusDetails = flowStatusDetails;
        this.statusInfo = statusInfo;
        this.maxLatency = maxLatency;
        this.maxLatencyTier2 = maxLatencyTier2;
        this.priority = priority;
        this.pinned = pinned;
        this.encapsulationType = encapsulationType;
        setDetectConnectedDevices(detectConnectedDevices);
        this.pathComputationStrategy = pathComputationStrategy;
        this.targetPathComputationStrategy = targetPathComputationStrategy;
        this.diverseWith = diverseWith;
        this.diverseWithYFlows = diverseWithYFlows;
        this.affinityWith = affinityWith;
        this.loopSwitchId = loopSwitchId;
        this.mirrorPointStatuses = mirrorPointStatuses;
        this.forwardLatency = forwardLatency;
        this.reverseLatency = reverseLatency;
        this.latencyLastModifiedTime = latencyLastModifiedTime;
        this.yFlowId = yFlowId;
        this.vlanStatistics = vlanStatistics;
    }

    /**
     * Returns whether this represents a forward flow.
     * The result is based on the cookie value,
     * see {@link FlowDto#cookieMarkedAsFroward} and {@link FlowDto#cookieMarkedAsReversed()}.
     */
    @JsonIgnore
    public boolean isForward() {
        boolean isForward = cookieMarkedAsFroward();
        boolean isReversed = cookieMarkedAsReversed();

        if (isForward && isReversed) {
            throw new IllegalArgumentException(
                    "Invalid cookie flags combinations - it mark as forward and reverse flow at same time.");
        }

        return isForward;
    }

    @JsonIgnore
    public boolean isReverse() {
        return !isForward();
    }

    private boolean cookieMarkedAsFroward() {
        boolean isMatch;

        if ((cookie & 0xE000000000000000L) != 0) {
            isMatch = (cookie & 0x4000000000000000L) != 0;
        } else {
            isMatch = (cookie & 0x0080000000000000L) == 0;
        }
        return isMatch;

    }

    private boolean cookieMarkedAsReversed() {
        boolean isMatch;
        if ((cookie & 0xE000000000000000L) != 0) {
            isMatch = (cookie & 0x2000000000000000L) != 0;
        } else {
            isMatch = (cookie & 0x0080000000000000L) != 0;
        }
        return isMatch;
    }

    /**
     * Checks creation params to figure out whether they are aligned or not.
      * @return validation result
     */
    @JsonIgnore
    public boolean isValid() {
        if (isAllocateProtectedPath() && isPinned()) {
            return false;
        }
        return true;
    }

    /**
     * Set connected devices flags.
     */
    public void setDetectConnectedDevices(DetectConnectedDevicesDto detectConnectedDevices) {
        if (detectConnectedDevices == null) {
            this.detectConnectedDevices = new DetectConnectedDevicesDto();
        } else {
            this.detectConnectedDevices = detectConnectedDevices;
        }
    }
}
