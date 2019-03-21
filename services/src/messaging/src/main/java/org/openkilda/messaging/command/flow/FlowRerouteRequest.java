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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.PathId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowRerouteRequest extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty("flowid")
    protected String flowId;

    @JsonProperty("path_ids")
    protected Set<PathId> pathIds;

    /**
     * Update flow even if path will not be changed.
     */
    @JsonProperty("force")
    private boolean force;

    public FlowRerouteRequest(String flowId, boolean force) {
        this(flowId, force, Collections.emptySet());
    }

    @JsonCreator
    public FlowRerouteRequest(@NonNull @JsonProperty("flowid") String flowId,
                              @JsonProperty("force") boolean force,
                              @NonNull @JsonProperty("path_ids") Set<PathId> pathIds) {
        this.flowId = flowId;
        this.force = force;
        this.pathIds = pathIds;
    }
}
