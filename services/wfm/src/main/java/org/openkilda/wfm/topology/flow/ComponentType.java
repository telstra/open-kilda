/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.flow;

/**
 * Represents components used in {@link FlowTopology}.
 */
public enum ComponentType {
    /**
     * Network cache dump spout. Receives network cache dump.
     */
    NETWORK_CACHE_SPOUT,

    /**
     * Northbound kafka spout. Receives Northbound requests.
     */
    NORTHBOUND_KAFKA_SPOUT,

    /**
     * Topology Engine kafka spout. Receives Topology Engine responses.
     */
    TOPOLOGY_ENGINE_KAFKA_SPOUT,

    /**
     * OpenFlow Speaker kafka spout. Receives OpenFlow Speaker responses.
     */
    SPEAKER_KAFKA_SPOUT,

    /**
     * Northbound kafka bolt. Sends Northbound responses.
     */
    NORTHBOUND_KAFKA_BOLT,

    /**
     * Northbound reply bolt. Forms Northbound responses.
     */
    NORTHBOUND_REPLY_BOLT,

    /**
     * Topology Engine kafka bolt. Sends Topology Engine requests.
     */
    TOPOLOGY_ENGINE_KAFKA_BOLT,

    /**
     * OpenFlow Speaker kafka bolt. Sends OpenFlow Speaker requests.
     */
    SPEAKER_KAFKA_BOLT,

    /**
     * Cache kafka bolt. Sends flows to cache topology.
     */
    CACHE_KAFKA_BOLT,

    /**
     * Splitter bolt. Processes flow requests and splits it on streams with flow-id fields.
     */
    SPLITTER_BOLT,

    /**
     * Command bolt to handle Speaker commands execution.
     */
    COMMAND_BOLT,

    /**
     * Crud bolt. Processes CRUD flow operations.
     */
    CRUD_BOLT,

    /**
     * Topology Engine bolt. Processes Topology Engine responses and splits it on streams with flow-id fields.
     */
    TOPOLOGY_ENGINE_BOLT,

    /**
     * OpenFlow Speaker bolt. Processes OpenFlow Speaker responses and splits it on streams with flow-id fields.
     */
    SPEAKER_BOLT,

    /**
     * Transaction bolt. Processes OpenFlow Speaker requests/responses and tracks its transactions id.
     */
    TRANSACTION_BOLT,

    /**
     * Error bolt. Processes errors.
     */
    ERROR_BOLT,

    LCM_SPOUT,
    LCM_FLOW_SYNC_BOLT,
    TOPOLOGY_ENGINE_OUTPUT,
}
