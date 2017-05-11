package org.bitbucket.openkilda.messaging.info;

import java.util.List;

public class PortStatsReply {

    private long xid;

    private List<PortStatsEntry> entries;

    public PortStatsReply(long xid, List<PortStatsEntry> entries) {
        this.xid = xid;
        this.entries = entries;
    }
}
