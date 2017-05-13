package org.bitbucket.openkilda.messaging.command;

import com.fasterxml.jackson.annotation.*;
import org.bitbucket.openkilda.messaging.StatsType;
import org.bitbucket.openkilda.messaging.Utils;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "switch_id",
        "type"
})
public class StatsRequest extends CommandData {

    /** The switch id to request stats from. It is a mandatory parameter. */
    protected String switchId;

    private StatsType statsType;

    @JsonCreator
    public StatsRequest(@JsonProperty("switch_id") String switchId, @JsonProperty("type") StatsType statsType) {
        setSwitchId(switchId);
        setStatsType(statsType);
    }

    @JsonProperty("switch_id")
    public String getSwitchId() {
        return switchId;
    }

    @JsonProperty("switch_id")
    public void setSwitchId(String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.switchId = switchId;
    }

    @JsonProperty("type")
    public StatsType getStatsType() {
        return statsType;
    }

    @JsonProperty("type")
    public void setStatsType(StatsType statsType) {
        if (statsType == null) {
            throw new IllegalArgumentException("need to specify stats type");
        }
        this.statsType = statsType;
    }
}
