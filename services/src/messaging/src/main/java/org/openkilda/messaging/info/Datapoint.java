/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.info;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;
import java.util.Objects;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Datapoint extends InfoData {

    /**
     * Metric name.
     */
    @JsonProperty("metric")
    private String metric;

    /**
     * Metric time.
     */
    @JsonProperty("time")
    private Long time;

    /**
     * Metric tags.
     */
    @JsonProperty("tags")
    private Map<String, String> tags;

    /**
     * Metric value.
     */
    @JsonProperty("value")
    private Number value;

    public Datapoint() {
    }

    @JsonCreator
    public Datapoint(@JsonProperty("metric") String metric,
                     @JsonProperty("time") Long time,
                     @JsonProperty("tags") Map<String, String> tags,
                     @JsonProperty("value") Number value) {
        this.metric = metric;
        this.time = time;
        this.tags = tags;
        this.value = value;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public Number getValue() {
        return value;
    }

    public void setValue(Number value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Datapoint datapoint = (Datapoint) o;
        return Objects.equals(metric, datapoint.getMetric())
                && Objects.equals(tags, datapoint.getTags())
                && Objects.equals(value, datapoint.getValue());
    }

    @Override
    public int hashCode() {
        int result = metric != null ? metric.hashCode() : 0;
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    public int simpleHashCode() {
        int result = metric != null ? metric.hashCode() : 0;
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("metric", metric)
                .add("time", time)
                .add("tags", tags)
                .add("value", value)
                .toString();
    }
}
