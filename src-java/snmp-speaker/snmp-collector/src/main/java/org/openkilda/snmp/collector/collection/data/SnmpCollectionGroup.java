/* Copyright 2020 Telstra Open Source
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

package org.openkilda.snmp.collector.collection.data;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

import java.util.List;

@Value
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnmpCollectionGroup {

    @JsonProperty("name")
    private String name;

    @JsonProperty("systems")
    private List<SnmpSystemDefinition> systems;

    @JsonProperty("groups")
    private List<SnmpMetricGroup> groups;

    @JsonCreator
    public SnmpCollectionGroup(@JsonProperty("name") String name,
                               @JsonProperty("systems") List<SnmpSystemDefinition> systems,
                               @JsonProperty("groups") List<SnmpMetricGroup> groups) {
        this.name = name;
        this.systems = systems;
        this.groups = groups;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("name", name)
                .add("systems", systems)
                .add("groups", groups)
                .toString();
    }

}
