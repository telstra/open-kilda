package org.bitbucket.openkilda.messaging.info;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.bitbucket.openkilda.messaging.StatsType;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "switch_id",
        "type",
        "stats"
})
public class FlowStatsData extends InfoData {

    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty
    private StatsType type = StatsType.FLOWS;

    @JsonProperty
    private List<FlowStatsReply> stats;

    public FlowStatsData(String switchId, List<FlowStatsReply> switchStats) {
        this.switchId = switchId;
        this.stats = switchStats;
    }
}
