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

/**
 * Represents information about an inter-switch link (ISL). This includes the source and destination, link status,
 * maximum and available bandwidth.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@RelationshipEntity(type = "isl")
public class Isl implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

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

    private int latency;

    private long speed;

    private int cost;

    @Property(name = "max_bandwidth")
    private long maxBandwidth;

    @Property(name = "default_max_bandwidth")
    private long defaultMaxBandwidth;

    @Property(name = "available_bandwidth")
    private long availableBandwidth;

    @Property(name = "status")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private IslStatus status;

    @Property(name = "actual")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private IslStatus actualStatus;

    @Property(name = "down_reason")
    @Convert(graphPropertyType = String.class)
    private IslDownReason downReason;

    @Property(name = "time_create")
    @Convert(InstantStringConverter.class)
    private Instant timeCreate;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    @Property(name = "under_maintenance")
    private boolean underMaintenance;

    @Property(name = "enable_bfd")
    private boolean enableBfd;

    @Property(name = "bfd_session_status")
    private String bfdSessionStatus;

    /**
     * Constructor used by the builder only and needed to copy srcSwitch to srcSwitchId, destSwitch to destSwitchId.
     */
    @Builder(toBuilder = true)
    Isl(Switch srcSwitch, Switch destSwitch, int srcPort, int destPort, //NOSONAR
            int latency, long speed, int cost, long maxBandwidth, long defaultMaxBandwidth,
            long availableBandwidth, IslStatus status, IslStatus actualStatus,
            Instant timeCreate, Instant timeModify, boolean underMaintenance, boolean enableBfd,
            String bfdSessionStatus) {
        setSrcSwitch(srcSwitch);
        setDestSwitch(destSwitch);
        this.srcPort = srcPort;
        this.destPort = destPort;
        this.latency = latency;
        this.speed = speed;
        this.cost = cost;
        this.maxBandwidth = maxBandwidth;
        this.defaultMaxBandwidth = defaultMaxBandwidth;
        this.availableBandwidth = availableBandwidth;
        this.status = status;
        this.actualStatus = actualStatus;
        this.timeCreate = timeCreate;
        this.timeModify = timeModify;
        this.underMaintenance = underMaintenance;
        this.enableBfd = enableBfd;
        this.bfdSessionStatus = bfdSessionStatus;
    }

    public final void setSrcSwitch(Switch srcSwitch) {
        this.srcSwitch = Objects.requireNonNull(srcSwitch);
        this.srcSwitchId = srcSwitch.getSwitchId();
    }

    public final void setDestSwitch(Switch destSwitch) {
        this.destSwitch = Objects.requireNonNull(destSwitch);
        this.destSwitchId = destSwitch.getSwitchId();
    }

    @Override
    public String toString() {
        return "Isl{"
                + "srcSwitch=" + srcSwitch.getSwitchId()
                + ", destSwitch=" + destSwitch.getSwitchId()
                + ", srcPort=" + srcPort
                + ", destPort=" + destPort
                + ", cost=" + cost
                + ", availableBandwidth=" + availableBandwidth
                + ", status=" + status
                + '}';
    }
}
