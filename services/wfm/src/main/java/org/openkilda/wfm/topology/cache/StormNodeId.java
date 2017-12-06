package org.openkilda.wfm.topology.cache;

public enum StormNodeId {
    ;

    private final String id;

    StormNodeId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
