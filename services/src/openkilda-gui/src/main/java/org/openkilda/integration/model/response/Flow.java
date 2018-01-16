package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Flow.
 * 
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "source", "destination", "maximum-bandwidth", "description",
        "last-updated"})
public class Flow {

    /** The id. */
    @JsonProperty("flowid")
    private String id;

    /** The source. */
    @JsonProperty("source")
    private FlowEndpoint source;

    /** The destination. */
    @JsonProperty("destination")
    private FlowEndpoint destination;

    /** The maximum bandwidth. */
    @JsonProperty("maximum-bandwidth")
    private int maximumBandwidth;

    /** The description. */
    @JsonProperty("description")
    private String description;

    /** The last updated. */
    @JsonProperty("last-updated")
    private String lastUpdated;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public FlowEndpoint getSource() {
        return source;
    }

    public void setSource(FlowEndpoint source) {
        this.source = source;
    }

    public FlowEndpoint getDestination() {
        return destination;
    }

    public void setDestination(FlowEndpoint destination) {
        this.destination = destination;
    }

    public int getMaximumBandwidth() {
        return maximumBandwidth;
    }

    public void setMaximumBandwidth(int maximumBandwidth) {
        this.maximumBandwidth = maximumBandwidth;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }


    @Override
    public String toString() {
        return "Flow [id=" + id + ", source=" + source + ", destination=" + destination
                + ", maximumBandwidth=" + maximumBandwidth + ", description=" + description
                + ", lastUpdated=" + lastUpdated + "]";
    }

}
