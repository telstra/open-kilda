package org.bitbucket.openkilda.wfm.topology.flow;

/**
 * Represents components used in {@link FlowTopology}.
 */
public enum ComponentType {
    /**
     * Northbound kafka spout.
     * Receives Northbound requests.
     */
    NORTHBOUND_KAFKA_SPOUT,

    /**
     * Topology Engine kafka spout.
     * Receives Topology Engine responses.
     */
    TOPOLOGY_ENGINE_KAFKA_SPOUT,

    /**
     * OpenFlow Speaker kafka spout.
     * Receives OpenFlow Speaker responses.
     */
    SPEAKER_KAFKA_SPOUT,

    /**
     * Northbound kafka bolt.
     * Sends Northbound responses.
     */
    NORTHBOUND_KAFKA_BOLT,

    /**
     * Northbound reply bolt.
     * Forms Northbound responses.
     */
    NORTHBOUND_REPLY_BOLT,

    /**
     * Topology Engine kafka bolt.
     * Sends Topology Engine requests.
     */
    TOPOLOGY_ENGINE_KAFKA_BOLT,

    /**
     * OpenFlow Speaker kafka bolt.
     * Sends OpenFlow Speaker requests.
     */
    SPEAKER_KAFKA_BOLT,

    /**
     * Northbound bolt.
     * Processes Northbound requests and splits it on streams with flow-id fields.
     */
    NORTHBOUND_REQUEST_BOLT,

    /**
     * Status bolt.
     * Processes all flow requests and tracks flows status.
     */
    STATUS_BOLT,

    /**
     * Topology Engine bolt.
     * Processes Topology Engine responses and splits it on streams with flow-id fields.
     */
    TOPOLOGY_ENGINE_BOLT,

    /**
     * OpenFlow Speaker bolt.
     * Processes OpenFlow Speaker responses and splits it on streams with flow-id fields.
     */
    SPEAKER_BOLT,

    /**
     * Transaction bolt.
     * Processes OpenFlow Speaker requests/responses and tracks its transactions id.
     */
    TRANSACTION_BOLT,

    /**
     * Error bolt.
     * Processes errors.
     */
    ERROR_BOLT
}
