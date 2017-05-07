package org.bitbucket.openkilda.messaging.payload;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Flow representation class.
 */
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "source",
        "destination",
        "maximum-bandwidth",
        "description",
        "last-updated"})
public class FlowPayload implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Flow id.
     */
    @JsonProperty("id")
    private String id;

    /**
     * Source endpoint.
     */
    @JsonProperty("source")
    private FlowEndpointPayload source;

    /**
     * Destination endpoint.
     */
    @JsonProperty("destination")
    private FlowEndpointPayload destination;

    /**
     * Bandwidth.
     */
    @JsonProperty("maximum-bandwidth")
    private Number maximumBandwidth;

    /**
     * FlowPayload description.
     */
    @JsonProperty("description")
    private String description;

    /**
     * Last flow updated timestamp.
     */
    @JsonProperty("last-updated")
    private String lastUpdated;

    /**
     * Constructs the flow model.
     */
    public FlowPayload() {
    }

    /**
     * Instance constructor.
     *
     * @param id                flow id
     * @param source            flow source
     * @param destination       flow destination
     * @param maximumBandwidth  flow maximum bandwidth
     * @param description       flow description
     * @param lastUpdated       flow last updated timestamp
     */
    @JsonCreator
    public FlowPayload(@JsonProperty("id") final String id,
                       @JsonProperty("source") final FlowEndpointPayload source,
                       @JsonProperty("destination") final FlowEndpointPayload destination,
                       @JsonProperty("maximum-bandwidth") final Number maximumBandwidth,
                       @JsonProperty("description") final String description,
                       @JsonProperty("last-updated") final String lastUpdated) {
        setId(id);
        setSource(source);
        setDestination(destination);
        setMaximumBandwidth(maximumBandwidth);
        setDescription(description);
        setLastUpdated(lastUpdated);
    }

    /**
     * Gets flow id.
     *
     * @return flow id
     */
    public String getId() {
        return id;
    }

    /**
     * Gets source endpoint.
     *
     * @return source endpoint
     */
    public FlowEndpointPayload getSource() {
        return source;
    }

    /**
     * Gets destination endpoint.
     *
     * @return destination endpoint
     */
    public FlowEndpointPayload getDestination() {
        return destination;
    }

    /**
     * Gets maximum-bandwidth.
     *
     * @return maximum-bandwidth
     */
    public Number getMaximumBandwidth() {
        return maximumBandwidth;
    }

    /**
     * Gets flow description.
     *
     * @return flow description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets last flow updated timestamp.
     *
     * @return last flow updated timestamp
     */
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets flow id.
     *
     * @param id flow id
     */
    public void setId(final String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("need to set id");
        }
        this.id = id;
    }

    /**
     * Sets source endpoint.
     *
     * @param source source endpoint
     */
    public void setSource(final FlowEndpointPayload source) {
        if (source == null) {
            throw new IllegalArgumentException("need to set source");
        }
        this.source = source;
    }

    /**
     * Sets destination endpoint.
     *
     * @param destination destination endpoint
     */
    public void setDestination(final FlowEndpointPayload destination) {
        if (destination == null) {
            throw new IllegalArgumentException("need to set destination");
        }
        this.destination = destination;
    }

    /**
     * Sets maximum bandwidth.
     *
     * @param maximumBandwidth maximum bandwidth
     */
    public void setMaximumBandwidth(final Number maximumBandwidth) {
        if (maximumBandwidth == null) {
            this.maximumBandwidth = 0;
        } else if (maximumBandwidth.intValue() >= 0) {
            this.maximumBandwidth = maximumBandwidth;
        } else {
            throw new IllegalArgumentException("need to set non negative bandwidth");
        }
        this.maximumBandwidth = maximumBandwidth;
    }

    /**
     * Sets flow description.
     *
     * @param description flow description
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Sets last flow updated timestamp.
     *
     * @param lastUpdated flow updated timestamp
     */
    public void setLastUpdated(final String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("id", id)
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

        if (obj == null || !(obj instanceof FlowPayload)) {
            return false;
        }

        FlowPayload that = (FlowPayload) obj;
        return Objects.equals(getId(), that.getId())
                && Objects.equals(getSource(), that.getSource())
                && Objects.equals(getDestination(), that.getDestination())
                && Objects.equals(getMaximumBandwidth(), that.getMaximumBandwidth())
                && Objects.equals(getDescription(), that.getDescription())
                && Objects.equals(getLastUpdated(), that.getLastUpdated());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, source, destination, maximumBandwidth, description, lastUpdated);
    }
}
