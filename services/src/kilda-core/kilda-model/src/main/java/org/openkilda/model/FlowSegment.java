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

import org.openkilda.model.converters.SwitchIdConverter;

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

import java.util.Objects;

/**
 * FlowSegment entity.
 */
@Data
@EqualsAndHashCode(exclude = "entityId")
@RelationshipEntity(type = "flow_segment")
public class FlowSegment {
    @Id
    @GeneratedValue
    private Long entityId;

    @Property(name = "flow_id")
    private String flowId;

    @Property(name = "cookie_id")
    private long cookieId;

    @StartNode
    private Switch srcSwitch;

    @EndNode
    private Switch destSwitch;

    @Setter(AccessLevel.NONE)
    @Property(name = "src_switch")
    @Convert(SwitchIdConverter.class)
    private SwitchId srcSwitchId;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "src_vlan")
    private int srcVlan;

    @Setter(AccessLevel.NONE)
    @Property(name = "dst_switch")
    @Convert(SwitchIdConverter.class)
    private SwitchId destSwitchId;

    @Property(name = "dst_port")
    private int destPort;

    @Property(name = "dst_vlan")
    private int destVlan;

    @Property(name = "transit_vlan")
    private int transitVlan;

    public void setSrcSwitch(Switch srcSwitch) {
        this.srcSwitch = Objects.requireNonNull(srcSwitch);
        this.srcSwitchId = srcSwitch.getSwitchId();
    }

    public void setDestSwitch(Switch destSwitch) {
        this.destSwitch = Objects.requireNonNull(destSwitch);
        this.destSwitchId = destSwitch.getSwitchId();
    }
}
