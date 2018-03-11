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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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
@JsonSerialize
public class FlowCacheSyncResults {
    private String[] droppedFlows;
    private String[] addedFlows;
    private String[] modifiedFlows;
    private String[] unchangedFlows;

    public FlowCacheSyncResults() {
        this(null,null,null,null);
    }

    public FlowCacheSyncResults(String[] droppedFlows, String[] addedFlows, String[] modifiedFlows, String[] unchangedFlows) {
        this.droppedFlows   = sanitizeArray(droppedFlows);
        this.addedFlows     = sanitizeArray(addedFlows);
        this.modifiedFlows  = sanitizeArray(modifiedFlows);
        this.unchangedFlows = sanitizeArray(unchangedFlows);
    }

    private String[] sanitizeArray(String[] strings){
        return (strings != null) ? strings : new String[0];
    }

    /**
     * @return array of FlowIDs for dropped flows
     */
    public String[] getDroppedFlows() {
        return droppedFlows;
    }

    /**
     * @return array of FlowIDs for added flows
     */
    public String[] getAddedFlows() {
        return addedFlows;
    }

    /**
     * @return array of FlowIDs:{field:oldvalue->newvalue} for modified flows
     */
    public String[] getModifiedFlows() {
        return modifiedFlows;
    }

    /**
     * @return array of FlowIDs for unchanged flows
     */
    public String[] getUnchangedFlows() {
        return unchangedFlows;
    }
}
