package org.bitbucket.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TODO: add javadoc.
 */
public class FlowStatsEntry {

    @JsonProperty
    private int tableId;

    @JsonProperty
    private long cookie;

    @JsonProperty
    private long packetCount;

    @JsonProperty
    private long byteCount;

    public FlowStatsEntry(@JsonProperty("tableId") int tableId,
                          @JsonProperty("cookie") long cookie,
                          @JsonProperty("packetCount") long packetCount,
                          @JsonProperty("byteCount") long byteCount) {
        this.tableId = tableId;
        this.cookie = cookie;
        this.packetCount = packetCount;
        this.byteCount = byteCount;
    }

    public int getTableId() {
        return tableId;
    }

    public long getCookie() {
        return cookie;
    }

    public long getPacketCount() {
        return packetCount;
    }

    public long getByteCount() {
        return byteCount;
    }
}
