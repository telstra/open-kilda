package org.bitbucket.openkilda.wfm.topology.cache;

/**
 * Represents stream used in {@link CacheTopology}.
 */
public enum StreamType {
    /**
     * State update.
     */
    WFM_UPDATE,

    /**
     * Dump cache.
     */
    WFM_DUMP,

    /**
     * Network storage update.
     */
    TPE,

    /**
     * Request cache dump.
     */
    CACHE_WFM
}
