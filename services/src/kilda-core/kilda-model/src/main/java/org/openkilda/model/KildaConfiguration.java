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
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@NodeEntity(label = "kilda_configuration")
public class KildaConfiguration {

    public static final KildaConfiguration DEFAULTS = KildaConfiguration.builder()
            .flowEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
            .build();

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @Property(name = "flow_encapsulation_type")
    @Convert(graphPropertyType = String.class)
    private FlowEncapsulationType flowEncapsulationType;

    @Property(name = "use_multi_table")
    private boolean useMultiTable;

    /**
     * Constructor prevents initialization of entityId field.
     */
    @Builder(toBuilder = true)
    KildaConfiguration(FlowEncapsulationType flowEncapsulationType,
                       boolean useMultiTable) {
        this.flowEncapsulationType = flowEncapsulationType;
        this.useMultiTable = useMultiTable;
    }
}
