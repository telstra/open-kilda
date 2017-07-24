package org.bitbucket.openkilda.messaging.command.routing;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.bitbucket.openkilda.messaging.Utils.FLOW_ID;

import org.bitbucket.openkilda.messaging.command.discovery.DiscoverIslCommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an command for flow path recomputation.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        FLOW_ID,
        "switch_id",
        "port_no"})
public class FlowReroute extends DiscoverIslCommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The flow id.
     */
    @JsonProperty(FLOW_ID)
    protected String id;

    /**
     * Default constructor.
     */
    public FlowReroute() {
    }

    /**
     * Instance constructor.
     *
     * @param id       flow id
     * @param switchId switch id
     * @param portNo   port number
     */
    @JsonCreator
    public FlowReroute(@JsonProperty(FLOW_ID) final String id,
                       @JsonProperty("switch_id") final String switchId,
                       @JsonProperty("port_no") final int portNo) {
        super(switchId, portNo);
        this.id = id;
        // TODO: use setId(id) after re-route by flow-id only implementation
    }

    /**
     * Returns the flow id.
     *
     * @return the flow id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the flow id.
     *
     * @param id flow id
     */
    public void setId(final String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("flow id must be set");
        }
        this.id = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(FLOW_ID, id)
                .add("switch_id", switchId)
                .add("port_no", portNo)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, switchId, portNo);
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

        FlowReroute that = (FlowReroute) object;
        return Objects.equals(getId(), that.getId())
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getPortNo(), that.getPortNo());
    }
}
