package org.bitbucket.openkilda.wfm.topology.flow;

/**
 * Represents stream used in {@link FlowTopology}.
 */
public enum StreamType {
    /**
     * Create flow topology stream.
     */
    CREATE,

    /**
     * Get flow(s) topology stream.
     */
    READ,

    /**
     * Update flow topology stream.
     */
    UPDATE,

    /**
     * Delete flow topology stream.
     */
    DELETE,

    /**
     * Restore flow topology stream.
     */
    RESTORE,

    /**
     * Reroute flow topology stream.
     */
    REROUTE,

    /**
     * Get flow path topology stream.
     */
    PATH,

    /**
     * Get flow status topology stream.
     */
    STATUS,

    /**
     * Flow command response.
     */
    RESPONSE,

    /**
     * Error messages.
     */
    ERROR;
}
