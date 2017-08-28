package org.bitbucket.openkilda.wfm.topology.cache;

/**
 * Represents components used in {@link CacheTopology}.
 */
public enum ComponentType {
    /**
     * Receives cache from storage.
     */
    CACHE_STORAGE_KAFKA_SPOUT,

    /**
     * Sends cache to storage.
     */
    CACHE_STORAGE_KAFKA_BOLT,

    /**
     * Receives cache dump request.
     */
    CACHE_DUMP_KAFKA_SPOUT,

    /**
     * Sends cache dump response.
     */
    CACHE_DUMP_KAFKA_BOLT,

    /**
     * Receives cache updates.
     */
    CACHE_UPDATE_KAFKA_SPOUT,

    /**
     * State bolt.
     */
    CACHE_BOLT,
}
