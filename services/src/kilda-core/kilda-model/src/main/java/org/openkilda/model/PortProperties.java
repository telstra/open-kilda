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

package org.openkilda.model;

import static java.lang.String.format;
import static org.neo4j.ogm.annotation.Relationship.INCOMING;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import java.io.Serializable;

@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "switchObj"})
@NodeEntity(label = "port_properties")
@ToString(exclude = {"switchObj"})
public class PortProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final boolean DISCOVERY_ENABLED_DEFAULT = true;

    public static PortPropertiesBuilder getDefault() {
        return PortProperties.builder()
                .discoveryEnabled(DISCOVERY_ENABLED_DEFAULT);
    }

    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Relationship(type = "owns", direction = INCOMING)
    private Switch switchObj;

    @Property(name = "port_no")
    private int port;

    @Property(name = "discovery_enabled")
    private boolean discoveryEnabled;

    @Property(name = "discriminator")
    private String discriminator;

    @Builder(toBuilder = true)
    public PortProperties(@NonNull Switch switchObj, int port, boolean discoveryEnabled) {
        this.switchObj = switchObj;
        this.port = port;
        this.discoveryEnabled = discoveryEnabled;
        this.discriminator = format("%s_%d", switchObj.getSwitchId().toString(), port);
    }
}
