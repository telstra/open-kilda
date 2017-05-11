package org.bitbucket.openkilda.messaging.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MeterConfigReply {

    @JsonProperty
    private long xid;

    @JsonProperty
    private List<Long> meterIds;

    @JsonCreator
    public MeterConfigReply(@JsonProperty long xid, @JsonProperty List<Long> meterIds) {
        this.xid = xid;
        this.meterIds = meterIds;
    }
}
