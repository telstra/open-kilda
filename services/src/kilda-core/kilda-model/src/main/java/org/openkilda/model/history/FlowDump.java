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

package org.openkilda.model.history;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

/**
 * Represents information about the flow state.
 * Contains all Flow state.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@NodeEntity(label = "flow_dump")
@Builder
@AllArgsConstructor
public class FlowDump {
    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @Property(name = "task_id")
    private String taskId;

    @Property(name = "flow_id")
    private String flowId;

    private long bandwidth;

    @Property(name = "ignoreBandwidth")
    private boolean ignoreBandwidth;

    @Property(name = "forward_cookie")
    private long forwardCookie;

    @Property(name = "reverse_cookie")
    private long reverseCookie;

    @Property(name = "src_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId sourceSwitch;

    @Property(name = "dst_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId destinationSwitch;

    @Property(name = "src_port")
    private int sourcePort;

    @Property(name = "dst_port")
    private int destinationPort;

    @Property(name = "src_vlan")
    private int sourceVlan;

    @Property(name = "dst_vlan")
    private int destinationVlan;

    @Property(name = "forward_meter_id")
    private Integer forwardMeterId;

    @Property(name = "reverse_meter_id")
    private Integer reverseMeterId;

    @Property(name = "forward_path")
    @Convert(graphPropertyType = String.class)
    private FlowPath forwardPath;

    @Property(name = "reverse_path")
    @Convert(graphPropertyType = String.class)
    private FlowPath reversePath;

    @Property(name = "forward_status")
    @Convert(graphPropertyType = String.class)
    private FlowStatus forwardStatus;

    @Property(name = "reverse_status")
    @Convert(graphPropertyType = String.class)
    private FlowStatus reverseStatus;
}
