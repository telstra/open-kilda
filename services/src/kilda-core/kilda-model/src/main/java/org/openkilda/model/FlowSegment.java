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

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a path segment of a flow and serves as a mark for ISLs used by the flow.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@RelationshipEntity(type = "flow_segment")
public class FlowSegment implements Serializable {
    private static final long serialVersionUID = 1L;

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

    /**
     * Hidden as used to support old storage schema.
     *
     * @deprecated Use cookie instead.
     */
    @Deprecated
    @Property(name = "parent_cookie")
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private long parentCookie;

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

    private long bandwidth;

    @Property(name = "ignore_bandwidth")
    private boolean ignoreBandwidth;

    @Property(name = "seq_id")
    private int seqId;

    @Property(name = "segment_latency")
    private Long latency;

    /**
     * Constructor used by the builder only and needed to copy srcSwitch to srcSwitchId, destSwitch to destSwitchId.
     */
    @Builder(toBuilder = true)
    FlowSegment(String flowId, long cookie, //NOSONAR
                Switch srcSwitch, Switch destSwitch, int srcPort, int destPort,
                long bandwidth, boolean ignoreBandwidth, int seqId, Long latency) {
        this.flowId = flowId;
        this.cookie = cookie;
        setSrcSwitch(srcSwitch);
        setDestSwitch(destSwitch);
        this.srcPort = srcPort;
        this.destPort = destPort;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.seqId = seqId;
        this.latency = latency;
    }

    public final void setSrcSwitch(Switch srcSwitch) {
        this.srcSwitch = Objects.requireNonNull(srcSwitch);
        this.srcSwitchId = srcSwitch.getSwitchId();
    }

    public final void setDestSwitch(Switch destSwitch) {
        this.destSwitch = Objects.requireNonNull(destSwitch);
        this.destSwitchId = destSwitch.getSwitchId();
    }

    public final void setCookie(long cookie) {
        this.cookie = cookie;
        this.parentCookie = cookie;
    }
}
