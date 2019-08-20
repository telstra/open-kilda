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

import org.openkilda.model.PortStatus;
import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.time.Instant;

@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@NodeEntity(label = "port_history")
@Builder
@AllArgsConstructor
public class PortHistory {
    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "switch_id")
    @Convert(graphPropertyType = String.class)
    private SwitchId switchId;

    @Property(name = "port_number")
    private int portNumber;

    @NonNull
    @Convert(graphPropertyType = String.class)
    private PortStatus finalStatus;

    @Property(name = "bounced_times")
    private int bouncedTimes;

    @NonNull
    @Convert(InstantStringConverter.class)
    @Property(name = "bouncing_started")
    private Instant bouncingStarted;

    @NonNull
    @Convert(InstantStringConverter.class)
    @Property(name = "bouncing_ended")
    private Instant bouncingEnded;
}
