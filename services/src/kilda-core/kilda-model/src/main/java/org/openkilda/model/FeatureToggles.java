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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(exclude = "entityId")
@NodeEntity(label = "config")
public class FeatureToggles {

    public static final FeatureToggles DEFAULTS = new FeatureToggles(
            null,  // ID
            false, // flows_reroute_on_isl_discovery
            false, // create_flow
            false, // update_flow
            false, // delete_flow
            false, // push_flow
            false, // unpush_flow
            true // use_bfd_for_isl_integrity_check
    );

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @Property(name = "flows_reroute_on_isl_discovery")
    private Boolean flowsRerouteOnIslDiscoveryEnabled;

    @Property(name = "create_flow")
    private Boolean createFlowEnabled;

    @Property(name = "update_flow")
    private Boolean updateFlowEnabled;

    @Property(name = "delete_flow")
    private Boolean deleteFlowEnabled;

    @Property(name = "push_flow")
    private Boolean pushFlowEnabled;

    @Property(name = "unpush_flow")
    private Boolean unpushFlowEnabled;

    @Property(name = "use_bfd_for_isl_integrity_check")
    private Boolean useBfdForIslIntegrityCheck;

    /**
     * Constructor prevents initialization of entityId field.
     */
    @Builder(toBuilder = true)
    FeatureToggles(Boolean flowsRerouteOnIslDiscoveryEnabled, Boolean createFlowEnabled, Boolean updateFlowEnabled,
                   Boolean deleteFlowEnabled, Boolean pushFlowEnabled, Boolean unpushFlowEnabled,
                   Boolean useBfdForIslIntegrityCheck) {
        this.flowsRerouteOnIslDiscoveryEnabled = flowsRerouteOnIslDiscoveryEnabled;
        this.createFlowEnabled = createFlowEnabled;
        this.updateFlowEnabled = updateFlowEnabled;
        this.deleteFlowEnabled = deleteFlowEnabled;
        this.pushFlowEnabled = pushFlowEnabled;
        this.unpushFlowEnabled = unpushFlowEnabled;
        this.useBfdForIslIntegrityCheck = useBfdForIslIntegrityCheck;
    }
}
