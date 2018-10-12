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
import lombok.Data;
import lombok.EqualsAndHashCode;
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
 * Isl entity.
 */
@Data
@EqualsAndHashCode(exclude = "entityId")
@RelationshipEntity(type = "isl")
public class Isl implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long entityId;

    @StartNode
    private Switch srcSwitch;

    @EndNode
    private Switch destSwitch;

    @Setter(AccessLevel.NONE)
    @Property(name = "src_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId srcSwitchId;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "src_status")
    private String srcStatus;

    @Property(name = "src_latency")
    private Long srcLatency;

    @Setter(AccessLevel.NONE)
    @Property(name = "dst_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId destSwitchId;

    @Property(name = "dst_port")
    private int destPort;

    @Property(name = "dst_status")
    private String destStatus;

    @Property(name = "dst_latency")
    private Long destLatency;

    private int latency;

    private long speed;

    private int cost;

    @Property(name = "max_bandwidth")
    private long maxBandwidth;

    @Property(name = "available_bandwidth")
    private long availableBandwidth;

    @Property(name = "status")
    @Convert(graphPropertyType = String.class)
    private IslStatus status;

    @Property(name = "actual")
    @Convert(graphPropertyType = String.class)
    private IslStatus actualStatus;

    @Property(name = "time_create")
    @Convert(InstantStringConverter.class)
    private Instant timeCreate;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    public void setSrcSwitch(Switch srcSwitch) {
        this.srcSwitch = Objects.requireNonNull(srcSwitch);
        this.srcSwitchId = srcSwitch.getSwitchId();
    }

    public void setDestSwitch(Switch destSwitch) {
        this.destSwitch = Objects.requireNonNull(destSwitch);
        this.destSwitchId = destSwitch.getSwitchId();
    }
}
