package org.bitbucket.openkilda.wfm.topology.cache;

/**
 * Represents components used in {@link CacheTopology}.
 */
public enum ComponentType {
    /**
     * Receives cache from storage.
     */
    STATE_STORAGE_KAFKA_SPOUT,

    /**
     * Sends cache to storage.
     */
    STATE_STORAGE_KAFKA_BOLT,

    /**
     * Receives cache dump request.
     */
    STATE_DUMP_KAFKA_SPOUT,

    /**
     * Sends cache dump response.
     */
    STATE_DUMP_KAFKA_BOLT,

    /**
     * Receives cache updates.
     */
    STATE_UPDATE_KAFKA_SPOUT,

    /**
     * State bolt.
     */
    STATE_BOLT
}
