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
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.time.Instant;

@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@NodeEntity(label = "link_props")
public class LinkProps {

    public static final String COST_PROP_NAME = "cost";
    public static final String MAX_BANDWIDTH_PROP_NAME = "max_bandwidth";

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "dst_port")
    private int dstPort;

    @Property(name = "src_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId srcSwitchId;

    @Property(name = "dst_switch")
    @Convert(graphPropertyType = String.class)
    private SwitchId dstSwitchId;

    private Integer cost;

    @Property(name = "max_bandwidth")
    private Long maxBandwidth;

    @Property(name = "time_create")
    @Convert(InstantStringConverter.class)
    private Instant timeCreate;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    /**
     * Constructor used by the builder only.
     */
    @Builder(toBuilder = true)
    LinkProps(int srcPort, int dstPort, SwitchId srcSwitchId, SwitchId dstSwitchId, Integer cost, //NOSONAR
              Long maxBandwidth, Instant timeCreate, Instant timeModify) {
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.srcSwitchId = srcSwitchId;
        this.dstSwitchId = dstSwitchId;
        this.cost = cost;
        this.maxBandwidth = maxBandwidth;
        this.timeCreate = timeCreate;
        this.timeModify = timeModify;
    }
}
