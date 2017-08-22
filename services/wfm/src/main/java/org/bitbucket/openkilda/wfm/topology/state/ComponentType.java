package org.bitbucket.openkilda.wfm.topology.state;

/**
 * Represents components used in {@link StateTopology}.
 */
public enum ComponentType {
    /**
     * Receives state from storage.
     */
    STATE_STORAGE_KAFKA_SPOUT,

    /**
     * Sends state to storage.
     */
    STATE_STORAGE_KAFKA_BOLT,

    /**
     * Receives state dump request.
     */
    STATE_DUMP_KAFKA_SPOUT,

    /**
     * Sends state dump response.
     */
    STATE_DUMP_KAFKA_BOLT,

    /**
     * Receives state updates.
     */
    STATE_UPDATE_KAFKA_SPOUT,

    /**
     * State bolt.
     */
    STATE_BOLT
}
