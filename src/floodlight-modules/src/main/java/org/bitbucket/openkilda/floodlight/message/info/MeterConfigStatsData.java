package org.bitbucket.openkilda.floodlight.message.info;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.bitbucket.openkilda.floodlight.message.StatsType;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "switch_id",
        "type",
        "stats"
})
public class MeterConfigStatsData extends InfoData {

    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty
    private StatsType type = StatsType.METERS;

    @JsonProperty
    private List<OFMeterConfigStatsReply> stats;

    public MeterConfigStatsData(String switchId, List<OFMeterConfigStatsReply> switchStats) {
        this.switchId = switchId;
        this.stats = switchStats;
    }
}
