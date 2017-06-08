package org.bitbucket.openkilda.messaging.info.stats;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * This class contains the flow stats replies for a given switch.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "destination",
        "switch_id",
        "stats"})
public class FlowStatsData extends InfoData {

    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty
    private List<FlowStatsReply> stats;

    public FlowStatsData(@JsonProperty("switch_id") String switchId,
                         @JsonProperty("stats") List<FlowStatsReply> switchStats) {
        this.switchId = switchId;
        this.stats = switchStats;
        setDestination(Destination.WFM);
    }

    public String getSwitchId() {
        return switchId;
    }

    public List<FlowStatsReply> getStats() {
        return stats;
    }
}
