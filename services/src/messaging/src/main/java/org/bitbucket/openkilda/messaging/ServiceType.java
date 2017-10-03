package org.bitbucket.openkilda.messaging;

public enum ServiceType {
    FLOW_TOPOLOGY("flow-storm-topology"),
    STATS_TOPOLOGY("statistics-storm-topology"),
    CACHE_TOPOLOGY("cache-storm-topology"),
    SPLITTER_TOPOLOGY("event-splitter-storm-topology"),
    WFM_TOPOLOGY("event-wfm-storm-topology");

    private final String id;

    ServiceType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
