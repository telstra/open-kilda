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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents information about a flow. This includes the source and destination, flow status,
 * bandwidth and description, encapsulation details (e.g. cookie, transitVlan, meterId).
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@RelationshipEntity(type = "flow")
public class Flow implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final long FORWARD_FLOW_COOKIE_MASK = 0x4000000000000000L;

    public static final long REVERSE_FLOW_COOKIE_MASK = 0x2000000000000000L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "flowid")
    private String flowId;

    @Property(name = "cookie")
    private long cookie;

    @NonNull
    @StartNode
    private Switch srcSwitch;

    @NonNull
    @EndNode
    private Switch destSwitch;

    /**
     * Hidden as used to support old storage schema.
     *
     * @deprecated Use srcSwitch instead.
     */
    @Deprecated
    @Property(name = "src_switch")
    @Convert(graphPropertyType = String.class)
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private SwitchId srcSwitchId;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "src_vlan")
    private int srcVlan;

    /**
     * Hidden as used to support old storage schema.
     *
     * @deprecated Use destSwitch instead.
     */
    @Deprecated
    @Property(name = "dst_switch")
    @Convert(graphPropertyType = String.class)
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private SwitchId destSwitchId;

    @Property(name = "dst_port")
    private int destPort;

    @Property(name = "dst_vlan")
    private int destVlan;

    /**
     * Representation of the flow path. As opposed to flow segment entity, this is kept within the flow entity.
     *
     * @deprecated This duplicates the information managed by flow segments, and considered excessive. Use flow segment
     *     entities instead.
     */
    @Deprecated
    @Property(name = "flowpath")
    @Convert(graphPropertyType = String.class)
    private FlowPath flowPath;

    private long bandwidth;

    private String description;

    /**
     * Hidden as used to support old storage schema.
     *
     * @deprecated Use timeModify instead.
     */
    @Deprecated
    @Property(name = "last_updated")
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private String lastUpdated;

    @Property(name = "transit_vlan")
    private int transitVlan;

    @Property(name = "meter_id")
    private Integer meterId;

    @Property(name = "ignore_bandwidth")
    private boolean ignoreBandwidth;

    @Property(name = "periodic_pings")
    private boolean periodicPings;

    @Property(name = "status")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private FlowStatus status;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    /**
     * Constructor used by the builder only and needed to copy srcSwitch to srcSwitchId, destSwitch to destSwitchId.
     */
    @Builder(toBuilder = true)
    Flow(String flowId, long cookie, //NOSONAR
            Switch srcSwitch, Switch destSwitch, int srcPort, int srcVlan, int destPort, int destVlan,
            FlowPath flowPath, long bandwidth, String description, int transitVlan,
            Integer meterId, boolean ignoreBandwidth, boolean periodicPings,
            FlowStatus status, Instant timeModify) {
        this.flowId = flowId;
        this.cookie = cookie;
        setSrcSwitch(srcSwitch);
        setDestSwitch(destSwitch);
        this.srcPort = srcPort;
        this.srcVlan = srcVlan;
        this.destPort = destPort;
        this.destVlan = destVlan;
        this.flowPath = flowPath;
        this.bandwidth = bandwidth;
        this.description = description;
        this.transitVlan = transitVlan;
        this.meterId = meterId;
        this.ignoreBandwidth = ignoreBandwidth;
        this.periodicPings = periodicPings;
        this.status = status;
        setTimeModify(timeModify);
    }

    public final void setSrcSwitch(Switch srcSwitch) {
        this.srcSwitch = Objects.requireNonNull(srcSwitch);
        this.srcSwitchId = srcSwitch.getSwitchId();
    }

    public final void setDestSwitch(Switch destSwitch) {
        this.destSwitch = Objects.requireNonNull(destSwitch);
        this.destSwitchId = destSwitch.getSwitchId();
    }

    /**
     * Getter which falls back to the lastUpdated if the timeModify is not set.
     */
    public final Instant getTimeModify() {
        if (timeModify != null) {
            return timeModify;
        } else if (lastUpdated != null) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(lastUpdated));
            } catch (NumberFormatException ex) {
                return Instant.parse(lastUpdated);
            }
        }
        return null;
    }

    public final void setTimeModify(Instant timeModify) {
        this.timeModify = timeModify;
        this.lastUpdated = timeModify != null ? Long.toString(timeModify.toEpochMilli()) : null;
    }

    /**
     * Checks whether a flow is forward.
     *
     * @return boolean flag
     */
    public boolean isForward() {
        boolean isForward = cookieMarkedAsFroward();
        boolean isReversed = cookieMarkedAsReversed();

        if (isForward && isReversed) {
            throw new IllegalArgumentException(
                    "Invalid cookie flags combinations - it mark as forward and reverse flow at same time.");
        }

        return isForward;
    }

    /**
     * Checks whether a flow is reverse.
     *
     * @return boolean flag
     */
    public boolean isReverse() {
        return !isForward();
    }

    /**
     * Checks whether the flow is through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isOneSwitchFlow() {
        return srcSwitch.getSwitchId().equals(destSwitch.getSwitchId());
    }

    public boolean isActive() {
        return status == FlowStatus.UP;
    }

    /**
     * Converts meter id to long value.
     * @return null if flow is unmetered otherwise returns converted meter id.
     */
    public Long getMeterLongValue() {
        return Optional.ofNullable(meterId)
                .map(Long::valueOf)
                .orElse(null);
    }

    private boolean cookieMarkedAsFroward() {
        boolean isMatch;

        if ((cookie & 0xE000000000000000L) != 0) {
            isMatch = (cookie & FORWARD_FLOW_COOKIE_MASK) != 0;
        } else {
            isMatch = (cookie & 0x0080000000000000L) == 0;
        }
        return isMatch;

    }

    private boolean cookieMarkedAsReversed() {
        boolean isMatch;
        if ((cookie & 0xE000000000000000L) != 0) {
            isMatch = (cookie & REVERSE_FLOW_COOKIE_MASK) != 0;
        } else {
            isMatch = (cookie & 0x0080000000000000L) != 0;
        }
        return isMatch;
    }
}
