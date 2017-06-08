package org.bitbucket.openkilda.wfm.topology.flow;

/**
 * Represents components used in {@link FlowTopology}.
 */
public enum ComponentType {
    /**
     * Northbound kafka spout.
     * Receives Northbound requests.
     */
    NB_KAFKA_SPOUT,

    /**
     * Topology Engine kafka spout.
     * Receives Topology Engine responses.
     */
    TE_KAFKA_SPOUT,

    /**
     * OpenFlow Speaker kafka spout.
     * Receives OpenFlow Speaker responses.
     */
    OFS_KAFKA_SPOUT,

    /**
     * Northbound kafka bolt.
     * Sends Northbound responses.
     */
    NB_KAFKA_BOLT,

    /**
     * Northbound reply bolt.
     * Forms Northbound responses.
     */
    NB_REPLY_BOLT,

    /**
     * Topology Engine kafka bolt.
     * Sends Topology Engine requests.
     */
    TE_KAFKA_BOLT,

    /**
     * OpenFlow Speaker kafka bolt.
     * Sends OpenFlow Speaker requests.
     */
    OFS_KAFKA_BOLT,

    /**
     * Northbound bolt.
     * Processes Northbound requests and splits it on streams with flow-id fields.
     */
    NB_REQUEST_BOLT,

    /**
     * Status bolt.
     * Processes all flow requests and tracks flows status.
     */
    STATUS_BOLT,

    /**
     * Topology Engine bolt.
     * Processes Topology Engine responses and splits it on streams with flow-id fields.
     */
    TE_BOLT,

    /**
     * OpenFlow Speaker bolt.
     * Processes OpenFlow Speaker responses and splits it on streams with flow-id fields.
     */
    OFS_BOLT,

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
