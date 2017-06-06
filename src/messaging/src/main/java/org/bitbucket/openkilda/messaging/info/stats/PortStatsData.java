package org.bitbucket.openkilda.messaging.info.stats;

import org.bitbucket.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * This class contains the port stats replies for a given switch.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "info",
        "switch_id",
        "stats"})
public class PortStatsData extends InfoData {

    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty
    private List<PortStatsReply> stats;

    public PortStatsData(@JsonProperty("switch_id") String switchId,
                         @JsonProperty("stats") List<PortStatsReply> switchStats) {
        this.switchId = switchId;
        this.stats = switchStats;
    }

    public String getSwitchId() {
        return switchId;
    }

    public List<PortStatsReply> getStats() {
        return stats;
    }
}
