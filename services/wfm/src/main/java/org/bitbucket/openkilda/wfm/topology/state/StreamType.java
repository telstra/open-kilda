package org.bitbucket.openkilda.wfm.topology.state;

/**
 * Represents stream used in {@link StateTopology}.
 */
public enum StreamType {
    /**
     * Storage update.
     */
    STORE,

    /**
     * Request state dump.
     */
    DUMP,

    /**
     * State update.
     */
    UPDATE
}
