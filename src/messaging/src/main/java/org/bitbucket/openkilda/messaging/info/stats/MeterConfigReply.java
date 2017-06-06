package org.bitbucket.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TODO: add javadoc.
 */
public class MeterConfigReply {

    @JsonProperty
    private long xid;

    @JsonProperty
    private List<Long> meterIds;

    @JsonCreator
    public MeterConfigReply(@JsonProperty("xid") long xid, @JsonProperty("meterIds") List<Long> meterIds) {
        this.xid = xid;
        this.meterIds = meterIds;
    }

    public long getXid() {
        return xid;
    }

    public List<Long> getMeterIds() {
        return meterIds;
    }
}
