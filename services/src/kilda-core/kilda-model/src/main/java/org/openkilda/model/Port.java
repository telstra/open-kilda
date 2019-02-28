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

import lombok.AccessLevel;
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
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import java.io.Serializable;

/**
 * Port entity.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "uniqueIndex"})
@NodeEntity(label = "port")
public class Port implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    // Hidden as set by the theSwitch field setter.
    @Property(name = "switch_id")
    @Setter(AccessLevel.NONE)
    @Convert(graphPropertyType = String.class)
    private SwitchId switchId;

    @NonNull
    @Relationship(type = "owned_by")
    private Switch theSwitch;

    @Property(name = "port_no")
    private int portNo;

    @Convert(graphPropertyType = String.class)
    private PortStatus status;

    // Hidden as used to imitate unique composite index for non-enterprise Neo4j versions.
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    @Property(name = "unique_index")
    private String uniqueIndex;

    @Builder(toBuilder = true)
    public Port(@NonNull Switch theSwitch, int portNo, PortStatus status) {
        this.portNo = portNo;
        this.status = status;

        setTheSwitch(theSwitch);
    }

    /**
     * Set the port and update related index(es).
     */
    public void setPortNo(int portNo) {
        this.portNo = portNo;
        calculateUniqueIndex();
    }

    /**
     * Set the switch and update related index(es).
     */
    public final void setTheSwitch(@NonNull Switch theSwitch) {
        this.theSwitch = theSwitch;
        this.switchId = theSwitch.getSwitchId();
        calculateUniqueIndex();
    }

    private void calculateUniqueIndex() {
        uniqueIndex = format("%s_%d", switchId, portNo);
    }
}
