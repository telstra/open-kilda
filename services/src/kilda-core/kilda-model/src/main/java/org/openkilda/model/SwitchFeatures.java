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


@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId"})
@NodeEntity(label = "switch_features")
@ToString(exclude = {"switchObj"})
public class SwitchFeatures {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Relationship(type = "has", direction = INCOMING)
    private Switch switchObj;

    @Property(name = "support_bfd")
    private boolean supportBfd;

    @Property(name = "support_vxlan_push_pop")
    private boolean supportVxlanPushPop;

    @Property(name = "support_vxlan_vni_match")
    private boolean supportVxlanVniMatch;

    @Builder(toBuilder = true)
    public SwitchFeatures(Switch switchObj, boolean supportBfd, boolean supportVxlanPushPop,
                          boolean supportVxlanVniMatch) {
        this.switchObj = switchObj;
        this.supportBfd = supportBfd;
        this.supportVxlanPushPop = supportVxlanPushPop;
        this.supportVxlanVniMatch = supportVxlanVniMatch;
    }
}
