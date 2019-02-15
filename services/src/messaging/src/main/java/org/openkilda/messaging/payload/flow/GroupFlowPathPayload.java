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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Represents flow path in diverse flow group.
 */
@Data
@NoArgsConstructor
public class GroupFlowPathPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The id of the flow.
     */
    @JsonProperty("flowid")
    protected String id;

    /**
     * The forward path of the flow.
     */
    @JsonProperty("flowpath_forward")
    protected List<PathNodePayload> forwardPath;

    /**
     * The reverse path of the flow.
     */
    @JsonProperty("flowpath_reverse")
    protected List<PathNodePayload> reversePath;

    @JsonProperty("overlapping_segments")
    protected OverlappingSegmentsStats segmentsStats;

    @Builder
    @JsonCreator
    public GroupFlowPathPayload(@JsonProperty("flowid") String id,
                                @JsonProperty("flowpath_forward") List<PathNodePayload> forwardPath,
                                @JsonProperty("flowpath_reverse") List<PathNodePayload> reversePath,
                                @JsonProperty("overlapping_segments") OverlappingSegmentsStats segmentsStats) {
        this.id = id;
        this.forwardPath = forwardPath;
        this.reversePath = reversePath;
        this.segmentsStats = segmentsStats;
    }
}
