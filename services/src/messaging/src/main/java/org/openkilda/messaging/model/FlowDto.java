/* Copyright 2019 Telstra Open Source
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
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowStatusDetails;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Data
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

    @JsonProperty("periodic-pings")
    private boolean periodicPings;

    @JsonProperty("allocate_protected_path")
    private boolean allocateProtectedPath;

    /**
     * Flow unmasked cookie.
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

    @JsonProperty("max_latency")
    private Integer maxLatency;

    @JsonProperty("priority")
    private Integer priority;

    @JsonProperty("pinned")
    private boolean pinned;

    @JsonProperty("encapsulation_type")
    private FlowEncapsulationType encapsulationType;

    public FlowDto() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId            flow id
     * @param bandwidth         bandwidth
     * @param ignoreBandwidth   ignore bandwidth flag
     * @param periodicPings     enable periodic pings
     * @param allocateProtectedPath allocate protected flow path.
     * @param cookie            cookie
     * @param description       description
     * @param createdTime       flow created timestamp
     * @param lastUpdated       last updated timestamp
     * @param sourceSwitch      source switch
     * @param destinationSwitch destination switch
     * @param sourcePort        source port
     * @param destinationPort   destination port
     * @param sourceVlan        source vlan id
     * @param destinationVlan   destination vlan id
     * @param meterId           meter id
     * @param transitEncapsulationId       transit vlan id
     * @param state             flow state
     * @param maxLatency        max latency
     * @param priority          flow priority
     * @param pinned            pinned flag
     * @param encapsulationType flow encapsulation type
     */
    @JsonCreator
    @Builder(toBuilder = true)
    public FlowDto(@JsonProperty(Utils.FLOW_ID) final String flowId,
                   @JsonProperty("bandwidth") final long bandwidth,
                   @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
                   @JsonProperty("periodic-pings") boolean periodicPings,
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
                   @JsonProperty("meter_id") final Integer meterId,
                   @JsonProperty("transit_encapsulation_id") final int transitEncapsulationId,
                   @JsonProperty("state") FlowState state,
                   @JsonProperty("status_details") FlowStatusDetails flowStatusDetails,
                   @JsonProperty("max_latency") Integer maxLatency,
                   @JsonProperty("priority") Integer priority,
                   @JsonProperty("pinned") boolean pinned,
                   @JsonProperty("encapsulation_type") FlowEncapsulationType encapsulationType) {
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
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
        this.transitEncapsulationId = transitEncapsulationId;
        this.meterId = meterId;
        this.state = state;
        this.flowStatusDetails = flowStatusDetails;
        this.maxLatency = maxLatency;
        this.priority = priority;
        this.pinned = pinned;
        this.encapsulationType = encapsulationType;
    }

    /**
     * Instance constructor.
     *
     * @param flowId            flow id
     * @param bandwidth         bandwidth
     * @param ignoreBandwidth   ignore bandwidth flag
     * @param description       description
     * @param sourceSwitch      source switch
     * @param sourcePort        source port
     * @param sourceVlan        source vlan id
     * @param destinationSwitch destination switch
     * @param destinationPort   destination port
     * @param destinationVlan   destination vlan id
     * @param pinned            pinned flag
     */
    public FlowDto(String flowId,
                   long bandwidth,
                   boolean ignoreBandwidth,
                   String description,
                   SwitchId sourceSwitch, int sourcePort, int sourceVlan,
                   SwitchId destinationSwitch, int destinationPort, int destinationVlan, boolean pinned) {
        this(flowId,
                bandwidth,
                ignoreBandwidth,
                false,
                false,
                0,
                description,
                null, null,
                sourceSwitch,
                destinationSwitch,
                sourcePort,
                destinationPort,
                sourceVlan,
                destinationVlan,
                null, 0, null, null, null, null, pinned, null);
    }

    public FlowDto(FlowPayload input) {
        this(input.getId(),
                input.getMaximumBandwidth(),
                input.isIgnoreBandwidth(),
                input.isPeriodicPings(),
                input.isAllocateProtectedPath(),
                0,
                input.getDescription(),
                null, null,
                input.getSource().getDatapath(),
                input.getDestination().getDatapath(),
                input.getSource().getPortNumber(),
                input.getDestination().getPortNumber(),
                input.getSource().getVlanId(),
                input.getDestination().getVlanId(),
                null, 0, null, null,
                input.getMaxLatency(),
                input.getPriority(),
                input.isPinned(),
                input.getEncapsulationType() != null ? FlowEncapsulationType.valueOf(
                        input.getEncapsulationType().toUpperCase()) : null);
    }

    @JsonIgnore
    public long getFlagglessCookie() {
        return cookie & MASK_COOKIE_FLAGS;
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
     * FlowDto to FlowDto comparison.
     *
     * <p>Ignore fields:
     * - ignoreBandwidth
     * - periodicPings
     * - cookie
     * - lastUpdated
     * - meterId
     * - transitEncapsulationId
     * - flowPath
     * FIXME(surabujin): drop/replace with lombok version (no usage found)
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        FlowDto flow = (FlowDto) object;
        return Objects.equals(getFlowId(), flow.getFlowId())
                && getBandwidth() == flow.getBandwidth()
                && Objects.equals(getDescription(), flow.getDescription())
                && getState() == flow.getState()
                && Objects.equals(getSourceSwitch(), flow.getSourceSwitch())
                && getSourcePort() == flow.getSourcePort()
                && getSourceVlan() == flow.getSourceVlan()
                && Objects.equals(getDestinationSwitch(), flow.getDestinationSwitch())
                && getDestinationPort() == flow.getDestinationPort()
                && getDestinationVlan() == flow.getDestinationVlan()
                && Objects.equals(getEncapsulationType(), flow.getEncapsulationType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowId, bandwidth, description, state, sourceSwitch, sourcePort, sourceVlan,
                destinationSwitch, destinationPort, destinationVlan, encapsulationType);
    }
}
