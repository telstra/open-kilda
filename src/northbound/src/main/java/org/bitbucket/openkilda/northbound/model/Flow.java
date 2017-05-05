package org.bitbucket.openkilda.northbound.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Flow representation class.
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Flow implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Flow id.
     */
    @JsonProperty("id")
    private String flowId;

    /**
     * Source endpoint.
     */
    @JsonProperty("source")
    private FlowEndpoint source;

    /**
     * Destination endpoint.
     */
    @JsonProperty("destination")
    private FlowEndpoint destination;

    /**
     * Bandwidth.
     */
    @JsonProperty("maximum-bandwidth")
    private Number maximumBandwidth;

    /**
     * Flow description.
     */
    @JsonProperty("description")
    private String description;

    /**
     * Last flow updated timestamp.
     */
    @JsonProperty("last-updated")
    private String lastUpdated;

    /**
     * Constructs the health-check model.
     */
    public Flow() {
    }

    /**
     * Gets flow id.
     *
     * @return flow id
     */
    @JsonProperty("id")
    public String getId() {
        return flowId;
    }

    /**
     * Gets source endpoint.
     *
     * @return source endpoint
     */
    @JsonProperty("source")
    public FlowEndpoint getSource() {
        return source;
    }

    /**
     * Gets destination endpoint.
     *
     * @return destination endpoint
     */
    @JsonProperty("destination")
    public FlowEndpoint getDestination() {
        return destination;
    }

    /**
     * Gets maximum-bandwidth.
     *
     * @return maximum-bandwidth
     */
    @JsonProperty("maximum-bandwidth")
    public Number getMaximumBandwidth() {
        return maximumBandwidth;
    }

    /**
     * Gets flow description.
     *
     * @return flow description
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * Gets last flow updated timestamp.
     *
     * @return last flow updated timestamp
     */
    @JsonProperty("last-updated")
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets flow id.
     *
     * @param flowId flow id
     */
    @JsonProperty("id")
    public void setId(final String flowId) {
        this.flowId = flowId;
    }

    /**
     * Sets source endpoint.
     *
     * @param source source endpoint
     */
    @JsonProperty("source")
    public void setSource(final FlowEndpoint source) {
        this.source = source;
    }

    /**
     * Sets destination endpoint.
     *
     * @param destination destination endpoint
     */
    @JsonProperty("destination")
    public void setDestination(final FlowEndpoint destination) {
        this.destination = destination;
    }

    /**
     * Sets maximum bandwidth.
     *
     * @param maximumBandwidth maximum bandwidth
     */
    @JsonProperty("maximum-bandwidth")
    public void setMaximumBandwidth(final Number maximumBandwidth) {
        this.maximumBandwidth = maximumBandwidth;
    }

    /**
     * Sets flow description.
     *
     * @param description flow description
     */
    @JsonProperty("description")
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Sets last flow updated timestamp.
     *
     * @param lastUpdated flow updated timestamp
     */
    @JsonProperty("last-updated")
    public void setSchemaDeploymentDate(final String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", flowId)
                .add("source", source)
                .add("destination", destination)
                .add("maximum-bandwidth", maximumBandwidth)
                .add("description", description)
                .add("last-updated", lastUpdated)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof Flow)) {
            return false;
        }

        Flow that = (Flow) obj;
        return Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getSource(), that.getSource())
                && Objects.equals(this.getDestination(), that.getDestination())
                && Objects.equals(this.getMaximumBandwidth(), that.getMaximumBandwidth())
                && Objects.equals(this.getDescription(), that.getDescription())
                && Objects.equals(this.getLastUpdated(), that.getLastUpdated());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(flowId, source, destination, maximumBandwidth, description, lastUpdated);
    }
}
