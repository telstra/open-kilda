package org.bitbucket.openkilda.messaging.command.stats;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeterConfigStatsRequest extends CommandData {
    /**
     * The switch id to request stats from. It is a mandatory parameter.
     */
    @JsonProperty("switch_id")
    protected String switchId;

    /**
     * Constructs meter config statistics request.
     *
     * @param switchId switch id
     */
    @JsonCreator
    public MeterConfigStatsRequest(@JsonProperty("switch_id") final String switchId) {
        setSwitchId(switchId);
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id
     */
    public void setSwitchId(String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.switchId = switchId;
    }
}
