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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"metric", "tags", "dps"})
public class SwitchPortStats {

    @JsonProperty("metric")
    private String metric;
    
    @JsonProperty("tags")
    private Tag tags;
    
    @JsonProperty("dps")
    private Map<String, Double> dps;
    
    public String getMetric() {
        return metric;
    }
    
    public void setMetric(String metric) {
        this.metric = metric;
    }
    
    public Tag getTags() {
        return tags;
    }
    
    public void setTags(Tag tags) {
        this.tags = tags;
    }
    
    public Map<String, Double> getDps() {
        return dps;
    }
    
    public void setDps(Map<String, Double> dps) {
        this.dps = dps;
    }
    
}
