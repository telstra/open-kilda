package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "source", "destination", "maximum-bandwidth", "description",
        "last-updated"})
public class Flow {

    @JsonProperty("flowid")
    private String id;

    @JsonProperty("source")
    private FlowEndpoint source;

    @JsonProperty("destination")
    private FlowEndpoint destination;

    @JsonProperty("maximum-bandwidth")
    private int maximumBandwidth;

    @JsonProperty("description")
    private String description;

    @JsonProperty("last-updated")
    private String lastUpdated;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public FlowEndpoint getSource() {
        return source;
    }

    public void setSource(final FlowEndpoint source) {
        this.source = source;
    }

    public FlowEndpoint getDestination() {
        return destination;
    }

    public void setDestination(final FlowEndpoint destination) {
        this.destination = destination;
    }

    public int getMaximumBandwidth() {
        return maximumBandwidth;
    }

    public void setMaximumBandwidth(final int maximumBandwidth) {
        this.maximumBandwidth = maximumBandwidth;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(final String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }


    @Override
    public String toString() {
        return "Flow [id=" + id + ", source=" + source + ", destination=" + destination
                + ", maximumBandwidth=" + maximumBandwidth + ", description=" + description
                + ", lastUpdated=" + lastUpdated + "]";
    }

}
