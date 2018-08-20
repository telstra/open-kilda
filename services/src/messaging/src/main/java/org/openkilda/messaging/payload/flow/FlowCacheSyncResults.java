/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.payload.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * FlowCacheSyncResults encapsulates the response from a FlowCacheSync call.
 *
 * There are four possibilities when the sync happens - Flows are either:
 *  - dropped (not in the DB, but was in the cache)
 *  - added (in the DB, but was not in the cache)
 *  - modified (was in the DB and the cache, but with different values)
 *  - unchanged (was in the DB and the cache, and they had the same values.
 *
 * The String arrays are the FlowIDs.  For Modified Flows, there is extra information wrt changes.
 */
@Value
public class FlowCacheSyncResults {

    /**
     * List of FlowIDs for dropped flows.
     */
    @JsonProperty("dropped_flows")
    private List<String> droppedFlows;

    /**
     * List of FlowIDs for added flows.
     */
    @JsonProperty("added_flows")
    private List<String> addedFlows;

    /**
     * List of FlowIDs:{field:oldvalue->newvalue} for modified flows.
     */
    @JsonProperty("modified_flows")
    private List<String> modifiedFlows;

    /**
     * List of FlowIDs for unchanged flows.
     */
    @JsonProperty("unchanged_flows")
    private List<String> unchangedFlows;

    @JsonCreator
    public FlowCacheSyncResults(
            @JsonProperty("dropped_flows") List<String> droppedFlows,
            @JsonProperty("added_flows") List<String> addedFlows,
            @JsonProperty("modified_flows") List<String> modifiedFlows,
            @JsonProperty("unchanged_flows") List<String> unchangedFlows) {
        this.droppedFlows = droppedFlows;
        this.addedFlows = addedFlows;
        this.modifiedFlows = modifiedFlows;
        this.unchangedFlows = unchangedFlows;
    }
}

