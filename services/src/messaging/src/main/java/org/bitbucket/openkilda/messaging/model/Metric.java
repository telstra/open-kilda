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

package org.bitbucket.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents OpenTSDB metric.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Metric {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Metric name.
     */
    @JsonProperty("metric")
    private String metric;

    /**
     * Metric tags.
     */
    @JsonProperty("tags")
    private Map<Object, Object> tags;

    /**
     * Metric aggregated tags.
     */
    @JsonProperty("aggregateTags")
    private List<String> aggregateTags;

    /**
     * Metric data points.
     */
    @JsonProperty("dps")
    private Map<String, Long> dps;

    /**
     * Default constructor.
     */
    public Metric() {
    }

    /**
     * Instance constructor.
     *
     * @param metric        metric name
     * @param tags          metric tags
     * @param aggregateTags metric aggregated tags
     * @param dps           metric data points
     */
    @JsonCreator
    public Metric(@JsonProperty("metric") String metric,
                  @JsonProperty("tags") Map<Object, Object> tags,
                  @JsonProperty("aggregateTags") List<String> aggregateTags,
                  @JsonProperty("dps") Map<String, Long> dps) {
        this.metric = metric;
        this.tags = tags;
        this.aggregateTags = aggregateTags;
        this.dps = dps;
    }

    /**
     * Gets metric name.
     *
     * @return metric name
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Sets metric name.
     *
     * @param metric metric name
     */
    public void setMetric(String metric) {
        this.metric = metric;
    }

    /**
     * Gets metric tags.
     *
     * @return metric tags
     */
    public Map<Object, Object> getTags() {
        return tags;
    }

    /**
     * Sets metric tags.
     *
     * @param tags metric tags
     */
    public void setTags(Map<Object, Object> tags) {
        this.tags = tags;
    }

    /**
     * Gets metric aggregated tags.
     *
     * @return metric aggregated tags
     */
    public List<String> getAggregateTags() {
        return aggregateTags;
    }

    /**
     * Sets metric aggregated tags.
     *
     * @param aggregateTags metric aggregated tags
     */
    public void setAggregateTags(List<String> aggregateTags) {
        this.aggregateTags = aggregateTags;
    }

    /**
     * Gets metric data points.
     *
     * @return metric data points
     */
    public Map<String, Long> getDps() {
        return dps;
    }

    /**
     * Sets metric data points.
     *
     * @param dps metric data points
     */
    public void setDps(Map<String, Long> dps) {
        this.dps = dps;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        Metric that = (Metric) object;
        return Objects.equals(getMetric(), that.getMetric()) &&
                Objects.equals(getTags(), that.getTags()) &&
                Objects.equals(getAggregateTags(), that.getAggregateTags()) &&
                Objects.equals(getDps(), that.getDps());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getMetric(), getTags(), getAggregateTags(), getDps());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("metric", metric)
                .add("tags", tags)
                .add("aggregateTags", aggregateTags)
                .add("dps", dps)
                .toString();
    }
}
