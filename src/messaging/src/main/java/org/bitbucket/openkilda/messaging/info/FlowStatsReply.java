package org.bitbucket.openkilda.messaging.info;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FlowStatsReply {

    @JsonProperty
    private long xid;

    @JsonProperty
    private List<FlowStatsEntry> entries;


    public FlowStatsReply(long xid, List<FlowStatsEntry> entries) {
        this.xid = xid;
        this.entries = entries;
    }
}
