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

import lombok.Data;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Properties;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import java.util.HashMap;
import java.util.Map;

@Data
@NodeEntity(label = "link_props")
public class LinkProps {

    @Id
    @GeneratedValue
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

    @Properties
    private Map<String, Object> properties = new HashMap<>();
}
