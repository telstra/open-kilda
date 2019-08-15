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
 * Represents flow LLDP resources.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId"})
@NodeEntity(label = "lldp_resources")
public class LldpResources implements Serializable {
    private static final long serialVersionUID = 6475278196979413383L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Index(unique = true)
    @Property(name = "flow_id")
    private String flowId;

    @Property(name = "src_meter_id")
    @Convert(graphPropertyType = Long.class)
    private MeterId srcMeterId;

    @Property(name = "dst_meter_id")
    @Convert(graphPropertyType = Long.class)
    private MeterId dstMeterId;

    @Builder(toBuilder = true)
    public LldpResources(@NonNull String flowId, MeterId srcMeterId, MeterId dstMeterId) {
        this.flowId = flowId;
        this.srcMeterId = srcMeterId;
        this.dstMeterId = dstMeterId;
    }
}
