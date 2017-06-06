package org.bitbucket.openkilda.messaging.payload.response;

import static com.google.common.base.Objects.toStringHelper;

import org.bitbucket.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Represents all flows northbound response.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"flow-status-list"})
public class FlowsStatusResponsePayload implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The name of the flow.
     */
    @JsonProperty("flow-status-list")
    protected List<FlowStatusResponsePayload> flowStatusList;

    /**
     * Default constructor.
     */
    public FlowsStatusResponsePayload() {
    }

    /**
     * Constructs instance.
     *
     * @param   flowStatusList  flow status list
     */
    @JsonCreator
    public FlowsStatusResponsePayload(@JsonProperty("flow-status-list")
                                          final List<FlowStatusResponsePayload> flowStatusList) {
        setFlowStatusList(flowStatusList);
    }

    /**
     * Returns list of flow status.
     *
     * @return  flow status list.
     */
    public List<FlowStatusResponsePayload> getFlowStatusList() {
        return flowStatusList;
    }

    /**
     * Sets list of flow stats.
     *
     * @param   flowStatusList  flow status list.
     */
    public void setFlowStatusList(final List<FlowStatusResponsePayload> flowStatusList) {
        this.flowStatusList = flowStatusList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("flow-status-list", flowStatusList)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(flowStatusList);
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

        FlowsStatusResponsePayload that = (FlowsStatusResponsePayload) object;
        return Objects.equals(getFlowStatusList(), that.getFlowStatusList());
    }
}
