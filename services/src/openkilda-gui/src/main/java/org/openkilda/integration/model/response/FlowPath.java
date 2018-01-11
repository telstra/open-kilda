package org.openkilda.integration.model.response;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class FlowPath.
 * 
 * @author Gaurav Chugh
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "source", "destination", "maximum-bandwidth", "description",
        "last-updated"})
public class FlowPath implements Serializable {

    /** The flowid. */
    @JsonProperty("flowid")
    private String flowid;

    /** The source. */
    @JsonProperty("source")
    private Source source;

    /** The destination. */
    @JsonProperty("destination")
    private Destination destination;

    /** The maximum bandwidth. */
    @JsonProperty("maximum-bandwidth")
    private int maximumBandwidth;

    /** The description. */
    @JsonProperty("description")
    private String description;

    /** The last updated. */
    @JsonProperty("last-updated")
    private String lastUpdated;


    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -7150467755772858386L;

    /**
     * Gets the flowid.
     *
     * @return the flowid
     */
    @JsonProperty("flowid")
    public String getFlowid() {
        return flowid;
    }

    /**
     * Sets the flowid.
     *
     * @param flowid the new flowid
     */
    @JsonProperty("flowid")
    public void setFlowid(String flowid) {
        this.flowid = flowid;
    }

    /**
     * Gets the source.
     *
     * @return the source
     */
    @JsonProperty("source")
    public Source getSource() {
        return source;
    }

    /**
     * Sets the source.
     *
     * @param source the new source
     */
    @JsonProperty("source")
    public void setSource(Source source) {
        this.source = source;
    }

    /**
     * Gets the destination.
     *
     * @return the destination
     */
    @JsonProperty("destination")
    public Destination getDestination() {
        return destination;
    }

    /**
     * Sets the destination.
     *
     * @param destination the new destination
     */
    @JsonProperty("destination")
    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    /**
     * Gets the maximum bandwidth.
     *
     * @return the maximum bandwidth
     */
    @JsonProperty("maximum-bandwidth")
    public int getMaximumBandwidth() {
        return maximumBandwidth;
    }

    /**
     * Sets the maximum bandwidth.
     *
     * @param maximumBandwidth the new maximum bandwidth
     */
    @JsonProperty("maximum-bandwidth")
    public void setMaximumBandwidth(int maximumBandwidth) {
        this.maximumBandwidth = maximumBandwidth;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the last updated.
     *
     * @return the last updated
     */
    @JsonProperty("last-updated")
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets the last updated.
     *
     * @param lastUpdated the new last updated
     */
    @JsonProperty("last-updated")
    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
