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

package org.openkilda.messaging.snmp.metric;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
public class SnmpMetricData extends InfoData {

    @JsonProperty("switch_name")
    private String switchName;
    @JsonProperty("metric_name")
    private String metricName;
    @JsonProperty("metrics")
    private List<SnmpMetricPoint> values;
    @JsonProperty("tags")
    private Map<String, String> tags;

    @JsonCreator
    public SnmpMetricData(
            @JsonProperty("switch_name") String switchName,
            @JsonProperty("metric_name") String metricName,
            @JsonProperty("metrics") List<SnmpMetricPoint> metrics,
            @JsonProperty("tags") Map<String, String> tags) {
        this.switchName = switchName;
        this.metricName = metricName;
        this.values = metrics;
        this.tags = tags;
    }
}
