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
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import java.io.Serializable;

/**
 * Represents a meter allocated for a flow path.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "uniqueIndex"})
@NodeEntity(label = "flow_meter")
public class FlowMeter implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "switch_id")
    @Index
    @Convert(graphPropertyType = String.class)
    private SwitchId switchId;

    @NonNull
    @Property(name = "meter_id")
    @Convert(graphPropertyType = Long.class)
    private MeterId meterId;

    @NonNull
    @Property(name = "flow_id")
    @Index
    private String flowId;

    @NonNull
    @Property(name = "path_id")
    @Index
    @Convert(graphPropertyType = String.class)
    private PathId pathId;

    // Hidden as used to imitate unique composite index for non-enterprise Neo4j versions.
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    @Property(name = "unique_index")
    @Index(unique = true)
    private String uniqueIndex;

    @Builder(toBuilder = true)
    public FlowMeter(@NonNull SwitchId switchId, @NonNull MeterId meterId,
                     @NonNull String flowId, @NonNull PathId pathId) {
        this.switchId = switchId;
        this.meterId = meterId;
        this.flowId = flowId;
        this.pathId = pathId;
        calculateUniqueIndex();
    }

    /**
     * Set the switch and update related index(es).
     */
    public final void setSwitchId(@NonNull SwitchId switchId) {
        this.switchId = switchId;
        calculateUniqueIndex();
    }

    /**
     * Set the meter and update related index(es).
     */
    public final void setMeterId(@NonNull MeterId meterId) {
        this.meterId = meterId;
        calculateUniqueIndex();
    }

    private void calculateUniqueIndex() {
        uniqueIndex = format("%s_%d", switchId, meterId != null ? meterId.getValue() : null);
    }
}
