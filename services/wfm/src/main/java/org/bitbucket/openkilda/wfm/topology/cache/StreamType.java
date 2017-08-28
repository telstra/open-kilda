package org.bitbucket.openkilda.wfm.topology.cache;

/**
 * Represents stream used in {@link CacheTopology}.
 */
public enum StreamType {
    /**
     * State update.
     */
    CACHE_UPDATE,

    /**
     * Storage update.
     */
    CACHE_TPE,

    /**
     * Storage update.
     */
    CACHE_REDIS,

    /**
     * Request cache dump.
     */
    CACHE_WFM
}
