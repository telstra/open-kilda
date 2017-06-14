package org.bitbucket.openkilda.messaging.payload.flow;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.bitbucket.openkilda.messaging.Utils.FLOW_ID;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
        FLOW_ID,
        "source",
        "destination",
        "maximum-bandwidth",
        "description",
        "last-updated",
        "cookie",
        "output-vlan-type"})
public class FlowPayload implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Cookie allocated for flow.
     */
    @JsonProperty("cookie")
    protected Long cookie;

    /**
     * Output action on the vlan tag.
     */
    @JsonProperty("output-vlan-type")
    protected OutputVlanType outputVlanType;

    /**
     * Flow id.
     */
    @JsonProperty(FLOW_ID)
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
    private Long maximumBandwidth;

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
     * Instance constructor.
     *
     * @param id               flow id
     * @param cookie           flow cookie
     * @param source           flow source
     * @param destination      flow destination
     * @param maximumBandwidth flow maximum bandwidth
     * @param description      flow description
     * @param lastUpdated      flow last updated timestamp
     * @param outputVlanType   flow output vlan tag action
     */
    @JsonCreator
    public FlowPayload(@JsonProperty(FLOW_ID) final String id,
                       @JsonProperty("cooke") final Long cookie,
                       @JsonProperty("source") final FlowEndpointPayload source,
                       @JsonProperty("destination") final FlowEndpointPayload destination,
                       @JsonProperty("maximum-bandwidth") final Long maximumBandwidth,
                       @JsonProperty("description") final String description,
                       @JsonProperty("last-updated") final String lastUpdated,
                       @JsonProperty("output-vlan-type") final OutputVlanType outputVlanType) {
        setId(id);
        setCookie(cookie);
        setSource(source);
        setDestination(destination);
        setMaximumBandwidth(maximumBandwidth);
        setDescription(description);
        setLastUpdated(lastUpdated);
        setOutputVlanType(outputVlanType);
    }

    /**
     * Instance constructor.
     *
     * @param id               flow id
     * @param source           flow source
     * @param destination      flow destination
     * @param maximumBandwidth flow maximum bandwidth
     * @param description      flow description
     * @param lastUpdated      flow last updated timestamp
     */
    public FlowPayload(final String id, final FlowEndpointPayload source,
                       final FlowEndpointPayload destination, final Long maximumBandwidth,
                       final String description, final String lastUpdated) {
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
     * Gets source endpoint.
     *
     * @return source endpoint
     */
    public FlowEndpointPayload getSource() {
        return source;
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
     * Gets destination endpoint.
     *
     * @return destination endpoint
     */
    public FlowEndpointPayload getDestination() {
        return destination;
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
     * Gets maximum-bandwidth.
     *
     * @return maximum-bandwidth
     */
    public Long getMaximumBandwidth() {
        return maximumBandwidth;
    }

    /**
     * Sets maximum bandwidth.
     *
     * @param maximumBandwidth maximum bandwidth
     */
    public void setMaximumBandwidth(final Long maximumBandwidth) {
        if (maximumBandwidth == null) {
            this.maximumBandwidth = 0L;
        } else if (maximumBandwidth >= 0L) {
            this.maximumBandwidth = maximumBandwidth;
        } else {
            throw new IllegalArgumentException("need to set non negative bandwidth");
        }
        this.maximumBandwidth = maximumBandwidth;
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
     * Sets flow description.
     *
     * @param description flow description
     */
    public void setDescription(final String description) {
        this.description = description;
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
     * Sets last flow updated timestamp.
     *
     * @param lastUpdated flow updated timestamp
     */
    public void setLastUpdated(final String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Returns cookie of the flow.
     *
     * @return cookie of the flow
     */
    public Long getCookie() {
        return cookie;
    }

    /**
     * Sets cookie for the flow.
     *
     * @param cookie for the flow
     */
    public void setCookie(final Long cookie) {
        this.cookie = cookie;
    }

    /**
     * Returns output action on the vlan tag.
     *
     * @return output action on the vlan tag
     */
    public OutputVlanType getOutputVlanType() {
        return outputVlanType;
    }

    /**
     * Sets output action on the vlan tag.
     *
     * @param outputVlanType action on the vlan tag
     */
    public void setOutputVlanType(final OutputVlanType outputVlanType) {
        this.outputVlanType = outputVlanType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(FLOW_ID, id)
                .add("cookie", cookie)
                .add("source", source)
                .add("destination", destination)
                .add("maximum-bandwidth", maximumBandwidth)
                .add("description", description)
                .add("last-updated", lastUpdated)
                .add("output-vlan-type", outputVlanType)
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
                && Objects.equals(getCookie(), that.getCookie())
                && Objects.equals(getSource(), that.getSource())
                && Objects.equals(getDestination(), that.getDestination())
                && Objects.equals(getMaximumBandwidth(), that.getMaximumBandwidth())
                && Objects.equals(getDescription(), that.getDescription())
                && Objects.equals(getLastUpdated(), that.getLastUpdated())
                && Objects.equals(getOutputVlanType(), that.getOutputVlanType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, cookie, source, destination, maximumBandwidth,
                description, lastUpdated, outputVlanType);
    }
}
