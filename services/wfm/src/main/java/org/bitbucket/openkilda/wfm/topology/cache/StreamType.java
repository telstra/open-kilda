package org.bitbucket.openkilda.wfm.topology.cache;

/**
 * Represents stream used in {@link CacheTopology}.
 */
public enum StreamType {
    /**
     * Storage update.
     */
    STORE,

    /**
     * Request cache dump.
     */
    DUMP,

    /**
     * State update.
     */
    UPDATE
}
