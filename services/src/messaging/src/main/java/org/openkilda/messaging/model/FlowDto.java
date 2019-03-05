/* Copyright 2017 Telstra Open Source
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
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
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
    String createdTime;

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
     * Flow transit vlan id.
     */
    @JsonProperty("transit_vlan")
    private int transitVlan;

    /**
     * Flow switch path.
     */
    @JsonProperty(Utils.FLOW_PATH)
    private PathInfoData flowPath;
    // TODO: why is PathInfoData an event, vs a model (ie package name)?

    /**
     * Flow state.
     */
    @JsonProperty("state")
    private FlowState state;

    @JsonProperty("max_latency")
    private Integer maxLatency;

    @JsonProperty("priority")
    private Integer priority;

    public FlowDto() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId            flow id
     * @param bandwidth         bandwidth
     * @param ignoreBandwidth   ignore bandwidth flag
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
     * @param transitVlan       transit vlan id
     * @param flowPath          flow switch path
     * @param state             flow state
     * @param maxLatency        max latency
     * @param priority          flow priority
     */
    @JsonCreator
    @Builder(toBuilder = true)
    public FlowDto(@JsonProperty(Utils.FLOW_ID) final String flowId,
                   @JsonProperty("bandwidth") final long bandwidth,
                   @JsonProperty("ignore_bandwidth") boolean ignoreBandwidth,
                   @JsonProperty("periodic-pings") boolean periodicPings,
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
                   @JsonProperty("transit_vlan") final int transitVlan,
                   @JsonProperty(Utils.FLOW_PATH) final PathInfoData flowPath,
                   @JsonProperty("state") FlowState state,
                   @JsonProperty("max_latency") Integer maxLatency,
                   @JsonProperty("priority") Integer priority) {
        this.flowId = flowId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.periodicPings = periodicPings;
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
        this.transitVlan = transitVlan;
        this.meterId = meterId;
        this.flowPath = flowPath;
        this.state = state;
        this.maxLatency = maxLatency;
        this.priority = priority;
    }

    /**
     * Copy constructor.
     */
    public FlowDto(FlowDto flow) {
        this(flow.getFlowId(),
                flow.getBandwidth(),
                flow.isIgnoreBandwidth(),
                flow.isPeriodicPings(),
                flow.getCookie(),
                flow.getDescription(),
                flow.getCreatedTime(),
                flow.getLastUpdated(),
                flow.getSourceSwitch(),
                flow.getDestinationSwitch(),
                flow.getSourcePort(),
                flow.getDestinationPort(),
                flow.getSourceVlan(),
                flow.getDestinationVlan(),
                flow.getMeterId(),
                flow.getTransitVlan(),
                flow.getFlowPath(),
                flow.getState(),
                flow.getMaxLatency(),
                flow.getPriority());
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
     */
    public FlowDto(String flowId,
                   long bandwidth,
                   boolean ignoreBandwidth,
                   String description,
                   SwitchId sourceSwitch, int sourcePort, int sourceVlan,
                   SwitchId destinationSwitch, int destinationPort, int destinationVlan) {
        this(flowId,
                bandwidth,
                ignoreBandwidth,
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
                null, 0, null, null, null, null);
    }

    public FlowDto(FlowPayload input) {
        this(input.getId(),
                input.getMaximumBandwidth(),
                input.isIgnoreBandwidth(),
                input.isPeriodicPings(),
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
                input.getPriority());
    }

    /**
     * Returns whether this is a single switch flow.
     */
    @JsonIgnore
    public boolean isOneSwitchFlow() {
        // TODO(surabujin): there is no decision how it should react on null values in source/dest switches
        if (sourceSwitch == null || destinationSwitch == null) {
            return sourceSwitch == null && destinationSwitch == null;
        }
        return sourceSwitch.equals(destinationSwitch);
    }

    @JsonIgnore
    public long getFlagglessCookie() {
        return cookie & MASK_COOKIE_FLAGS;
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
     * Checks if flow path contains specified switch.
     *
     * @param switchId switch id
     * @return true if flow path contains specified switch
     */
    public boolean containsSwitchInPath(SwitchId switchId) {
        return flowPath.getPath().stream()
                .anyMatch(node -> node.getSwitchId().equals(switchId));
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
     * - transitVlan
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
                && getDestinationVlan() == flow.getDestinationVlan();
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowId, bandwidth, description, state,
                sourceSwitch, sourcePort, sourceVlan, destinationSwitch, destinationPort, destinationVlan);
    }
}
