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
 * Represents stream used in {@link FlowTopology}.
 */
public enum StreamType {
    /**
     * Create flow topology stream.
     */
    CREATE,

    /**
     * Get flow(s) topology stream.
     */
    READ,

    /**
     * Update flow topology stream.
     */
    UPDATE,

    /**
     * Delete flow topology stream.
     */
    DELETE,

    /**
     * Push pre-existing flows.
     */
    PUSH,

    /**
     * Unpush (delete) pre-existing flows.
     */
    UNPUSH,

    /**
     * Restore flow topology stream.
     */
    RESTORE,

    /**
     * Reroute flow topology stream.
     */
    REROUTE,

    /**
     * Get flow path topology stream.
     */
    PATH,

    /**
     * Get flow status topology stream.
     */
    STATUS,

    /**
     * Sync Caches with DB.
     */
    CACHE_SYNC,

    VERIFICATION,

    /**
     * Flow command response.
     */
    RESPONSE,

    /**
     * Error messages.
     */
    ERROR;
}
