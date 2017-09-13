package org.bitbucket.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Metric {
    private static final long serialVersionUID = 1L;

    @JsonProperty("metric")
    private String metric;

    @JsonProperty("tags")
    private Map<Object, Object> tags;

    @JsonProperty("aggregateTags")
    private List<String> aggregateTags;

    @JsonProperty("dps")
    private Map<String, Long> dps;

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

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("metric", metric)
                .add("tags", tags)
                .add("aggregateTags", aggregateTags)
                .add("dps", dps)
                .toString();
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Map<Object, Object> getTags() {
        return tags;
    }

    public void setTags(Map<Object, Object> tags) {
        this.tags = tags;
    }

    public List<String> getAggregateTags() {
        return aggregateTags;
    }

    public void setAggregateTags(List<String> aggregateTags) {
        this.aggregateTags = aggregateTags;
    }

    public Map<String, Long> getDps() {
        return dps;
    }

    public void setDps(Map<String, Long> dps) {
        this.dps = dps;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        Metric metric1 = (Metric) object;
        return Objects.equals(getMetric(), metric1.getMetric()) &&
                Objects.equals(getTags(), metric1.getTags()) &&
                Objects.equals(getAggregateTags(), metric1.getAggregateTags()) &&
                Objects.equals(getDps(), metric1.getDps());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMetric(), getTags(), getAggregateTags(), getDps());
    }
}
