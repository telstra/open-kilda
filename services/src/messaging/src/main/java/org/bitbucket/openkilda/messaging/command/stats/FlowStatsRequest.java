package org.bitbucket.openkilda.messaging.command.stats;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowStatsRequest extends CommandData {
    /**
     * The flow id to request stats for. It is a mandatory parameter.
     */
    @JsonProperty("switch_id")
    protected String flowId;

    /**
     * Constructs statistics request.
     *
     * @param flowId switch id
     */
    @JsonCreator
    public FlowStatsRequest(@JsonProperty("switch_id") final String flowId) {
        setFlowId(flowId);
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Sets switch id.
     *
     * @param flowId switch id
     */
    public void setFlowId(String flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(flowId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.flowId = flowId;
    }
}
