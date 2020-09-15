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
public class SnmpMetricGroup {

    @JsonProperty("name")
    private String name;

    @JsonProperty("instance")
    private String instance;

    @JsonProperty("metrics")
    private List<SnmpMetricEntry> metrics;

    @JsonCreator
    public SnmpMetricGroup(@JsonProperty("name") String name,
                           @JsonProperty("instance") String instance,
                           @JsonProperty("metrics") List<SnmpMetricEntry> metrics) {
        this.name = name;
        this.instance = instance;
        this.metrics = metrics;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("name", name)
                .add("instance", instance)
                .add("groups", metrics)
                .toString();

    }
}
