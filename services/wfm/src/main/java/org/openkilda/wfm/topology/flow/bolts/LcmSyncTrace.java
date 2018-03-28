package org.openkilda.wfm.topology.flow.bolts;

import org.apache.storm.tuple.Tuple;

import java.util.UUID;

public class LcmSyncTrace {
    private final Tuple syncRequest;
    private final UUID correlationId = UUID.randomUUID();

    public LcmSyncTrace(Tuple syncRequest) {
        this.syncRequest = syncRequest;
    }

    public Tuple getSyncRequest() {
        return syncRequest;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }
}
