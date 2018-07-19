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

package org.openkilda.messaging.payload.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
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
@JsonPropertyOrder({
        Utils.FLOW_ID,
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
    @JsonProperty(Utils.FLOW_ID)
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
    private int maximumBandwidth;

    /**
     * If SET ignore bandwidth in path computation
     */
    @JsonProperty("ignore_bandwidth")
    private boolean ignoreBandwidth = false;

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

    @JsonProperty("status")
    private String status;

    /**
     * Instance constructor.
     *
     * @param id               flow id
     * @param source           flow source
     * @param destination      flow destination
     * @param maximumBandwidth flow maximum bandwidth
     * @param ignoreBandwidth  should ignore bandwidth in path computation
     * @param description      flow description
     * @param lastUpdated      flow last updated timestamp
     */
    @JsonCreator
    public FlowPayload(@JsonProperty(Utils.FLOW_ID) String id,
                       @JsonProperty("source") FlowEndpointPayload source,
                       @JsonProperty("destination") FlowEndpointPayload destination,
                       @JsonProperty("maximum-bandwidth") int maximumBandwidth,
                       @JsonProperty("ignore_bandwidth") Boolean ignoreBandwidth,
                       @JsonProperty("description") String description,
                       @JsonProperty("last-updated") String lastUpdated,
                       @JsonProperty("status") String status) {
        setId(id);
        setSource(source);
        setDestination(destination);
        setMaximumBandwidth(maximumBandwidth);
        setIgnoreBandwidth(ignoreBandwidth);
        setDescription(description);
        setLastUpdated(lastUpdated);
        setStatus(status);
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
    public void setId(String id) {
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
    public void setSource(FlowEndpointPayload source) {
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
    public void setDestination(FlowEndpointPayload destination) {
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
    public int getMaximumBandwidth() {
        return maximumBandwidth;
    }

    /**
     * Sets maximum bandwidth.
     *
     * @param maximumBandwidth maximum bandwidth
     */
    public void setMaximumBandwidth(int maximumBandwidth) {
        if (maximumBandwidth >= 0L) {
            this.maximumBandwidth = maximumBandwidth;
        } else {
            throw new IllegalArgumentException("need to set non negative bandwidth");
        }
    }

    public boolean isIgnoreBandwidth() {
        return ignoreBandwidth;
    }

    /**
     * Sets ignore bandwidth flag.
     *
     * @param ignoreBandwidth flag value
     */
    public void setIgnoreBandwidth(Boolean ignoreBandwidth) {
        if (ignoreBandwidth == null) {
            ignoreBandwidth = false;
        }
        this.ignoreBandwidth = ignoreBandwidth;
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
    public void setDescription(String description) {
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
    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(Utils.FLOW_ID, id)
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
