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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantLongConverter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Flow entity.
 */
@Data
@EqualsAndHashCode(exclude = "entityId")
@RelationshipEntity(type = "flow")
public class Flow implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @Property(name = "flowid")
    private String flowId;

    @Property(name = "cookie")
    private long cookie;

    @StartNode
    private Switch srcSwitch;

    @EndNode
    private Switch destSwitch;

    @Property(name = "src_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId srcSwitchId;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "src_vlan")
    private int srcVlan;

    @Property(name = "dst_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId destSwitchId;

    @Property(name = "dst_port")
    private int destPort;

    @Property(name = "dst_vlan")
    private int destVlan;

    @Property(name = "flowpath")
    @Convert(graphPropertyType = String.class)
    private Path flowPath;

    private long bandwidth;

    private String description;

    @Property(name = "last_updated")
    @Convert(InstantLongConverter.class)
    private Instant lastUpdated;

    @Property(name = "transit_vlan")
    private int transitVlan;

    @Property(name = "meter_id")
    private int meterId;

    @Property(name = "ignore_bandwidth")
    private boolean ignoreBandwidth;

    @Property(name = "periodic-pings")
    private boolean periodicPings;

    @Property(name = "flow_type")
    private OutputVlanType flowType;

    @Property(name = "status")
    private FlowStatus status;

    public void setSrcSwitch(Switch srcSwitch) {
        this.srcSwitch = Objects.requireNonNull(srcSwitch);
        this.srcSwitchId = srcSwitch.getSwitchId();
    }

    public void setDestSwitch(Switch destSwitch) {
        this.destSwitch = Objects.requireNonNull(destSwitch);
        this.destSwitchId = destSwitch.getSwitchId();
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
}
