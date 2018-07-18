package org.openkilda.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
