package org.bitbucket.openkilda.messaging.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.bitbucket.openkilda.messaging.Utils;

/**
 * Represents general flow info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "flow_name",
        "switch_id"
})
public class AbstractFlow extends CommandData {
    /** The flow name. It is a mandatory parameter. */
    protected String flowName;
    /** The switch id to manage flow on. It is a mandatory parameter. */
    protected String switchId;

    /**
     * Constructs an abstract flow installation command.
     *
     * @param flowName    Flow name
     * @param switchId    Switch id for flow management
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public AbstractFlow(@JsonProperty("flow_name") String flowName,
                        @JsonProperty("switch_id") String switchId) {
        setFlowName(flowName);
        setSwitchId(switchId);
    }

    /**
     * Returns the flow name.
     *
     * @return the flow name
     */
    @JsonProperty("flow_name")
    public String getFlowName() {
        return flowName;
    }

    /**
     * Returns id of the switch.
     *
     * @return ID of the switch
     */
    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets the flow name.
     *
     * @param flowName flow name
     */
    @JsonProperty("flow_name")
    public void setFlowName(String flowName) {
        if (flowName == null || flowName.isEmpty()) {
            throw new IllegalArgumentException("flowName must be set");
        }
        this.flowName = flowName;
    }

    /**
     * Sets id of the switch.
     *
     * @param switchId of the switch
     */
    @JsonProperty("switch_id")
    public void setSwitchId(String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.switchId = switchId;
    }
}
