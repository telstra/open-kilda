package org.bitbucket.openkilda.messaging.info;

public class FlowStatsEntry {

    private short tableId;
    private long cookie;
    private long packetCount;
    private long byteCount;

    public FlowStatsEntry(short tableId, long cookie, long packetCount, long byteCount) {
        this.tableId = tableId;
        this.cookie = cookie;
        this.packetCount = packetCount;
        this.byteCount = byteCount;
    }
}
