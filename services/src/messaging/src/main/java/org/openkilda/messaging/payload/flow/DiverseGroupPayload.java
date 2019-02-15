/* Copyright 2019 Telstra Open Source
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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class DiverseGroupPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Represents intersection stats between current flow and other flows in group.
     */
    @JsonProperty("overlapping_segments")
    OverlappingSegmentsStats overlappingSegments;

    @JsonProperty("other_flows")
    List<GroupFlowPathPayload> otherFlows;

    @JsonCreator
    public DiverseGroupPayload(@JsonProperty("overlapping_segments") OverlappingSegmentsStats overlappingSegments,
                               @JsonProperty("other_flows") List<GroupFlowPathPayload> otherFlows) {
        this.overlappingSegments = overlappingSegments;
        this.otherFlows = otherFlows;
    }
}
