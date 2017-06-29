package org.bitbucket.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TODO: add javadoc.
 */
public class FlowStatsReply {

    @JsonProperty
    private long xid;

    @JsonProperty
    private List<FlowStatsEntry> entries;

    public FlowStatsReply(@JsonProperty("xid") long xid, @JsonProperty("entries") List<FlowStatsEntry> entries) {
        this.xid = xid;
        this.entries = entries;
    }

    public List<FlowStatsEntry> getEntries() {
        return entries;
    }
}
