package org.bitbucket.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TODO: add javadoc.
 */
public class PortStatsReply {

    @JsonProperty
    private long xid;

    @JsonProperty
    private List<PortStatsEntry> entries;

    @JsonCreator
    public PortStatsReply(@JsonProperty("xid") long xid, @JsonProperty("entries") List<PortStatsEntry> entries) {
        this.xid = xid;
        this.entries = entries;
    }

    public long getXid() {
        return xid;
    }

    public List<PortStatsEntry> getEntries() {
        return entries;
    }
}
