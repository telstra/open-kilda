package org.bitbucket.openkilda.wfm.topology.cache;

/**
 * Represents components used in {@link CacheTopology}.
 */
public enum ComponentType {
    /**
     * Receives cache from storage.
     */
    TPE_KAFKA_SPOUT,

    /**
     * Sends cache to storage.
     */
    TPE_KAFKA_BOLT,

    /**
     * Sends cache dump to wfm.
     */
    WFM_DUMP_KAFKA_BOLT,

    /**
     * Receives cache updates.
     */
    WFM_UPDATE_KAFKA_SPOUT,

    /**
     * State bolt.
     */
    CACHE_BOLT
}
