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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.typeconversion.Convert;

/**
 * Represents information about the flow event.
 * The event has an actor and represents actions from outside of Kilda. Contains all Flow state.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@NodeEntity(label = "flowEvent")
@Builder
@AllArgsConstructor
public class FlowEvent {

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    private Long entityId;

    private Long timestamp;

    private String actor;
    private String action;
    private String taskId;
    private String details;

    private String flowId;

    private long bandwidth;

    private boolean ignoreBandwidth;

    private long forwardCookie;

    private long reverseCookie;

    @Convert(graphPropertyType = String.class)
    private SwitchId sourceSwitch;

    @Convert(graphPropertyType = String.class)
    private SwitchId destinationSwitch;

    private int sourcePort;

    private int destinationPort;

    private int sourceVlan;

    private int destinationVlan;

    private Integer forwardMeterId;

    private Integer reverseMeterId;

    @Convert(graphPropertyType = String.class)
    private FlowPath forwardPath;

    @Convert(graphPropertyType = String.class)
    private FlowPath reversePath;

    @Convert(graphPropertyType = String.class)
    private FlowStatus forwardStatus;

    @Convert(graphPropertyType = String.class)
    private FlowStatus reverseStatus;
}
