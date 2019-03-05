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

package org.openkilda.messaging.model;

import org.openkilda.messaging.payload.flow.OverlappingSegmentsStats;
import org.openkilda.messaging.payload.flow.PathNodePayload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Represents the flow paths response item.
 */
@Data
@NoArgsConstructor
public class FlowPathDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("flowid")
    private String id;

    /**
     * Indicates that stats in current DTO corresponds with primary asked flow path.
     */
    @JsonProperty("primary_path_stat")
    private boolean primaryPathCorrespondStat = true;

    @JsonProperty("flowpath_forward")
    private List<PathNodePayload> forwardPath;

    @JsonProperty("flowpath_reverse")
    private List<PathNodePayload> reversePath;

    @JsonProperty("overlapping_segments")
    private OverlappingSegmentsStats segmentsStats;

    @JsonProperty("protected_path")
    private FlowProtectedPathDto protectedPath;

    @Builder
    @JsonCreator
    public FlowPathDto(@JsonProperty("flowid") String id,
                       @JsonProperty("primary_path_stat") boolean primaryPathCorrespondStat,
                       @JsonProperty("flowpath_forward") List<PathNodePayload> forwardPath,
                       @JsonProperty("flowpath_reverse") List<PathNodePayload> reversePath,
                       @JsonProperty("overlapping_segments") OverlappingSegmentsStats segmentsStats,
                       @JsonProperty("protected_path") FlowProtectedPathDto protectedPath) {
        this.id = id;
        this.primaryPathCorrespondStat = primaryPathCorrespondStat;
        this.forwardPath = forwardPath;
        this.reversePath = reversePath;
        this.segmentsStats = segmentsStats;
        this.protectedPath = protectedPath;
    }

    @Data
    @NoArgsConstructor
    public static class FlowProtectedPathDto implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("flowpath_forward")
        private List<PathNodePayload> forwardPath;

        @JsonProperty("flowpath_reverse")
        private List<PathNodePayload> reversePath;

        @JsonProperty("overlapping_segments")
        private OverlappingSegmentsStats segmentsStats;

        @Builder
        @JsonCreator
        public FlowProtectedPathDto(@JsonProperty("flowpath_forward") List<PathNodePayload> forwardPath,
                            @JsonProperty("flowpath_reverse") List<PathNodePayload> reversePath,
                            @JsonProperty("overlapping_segments") OverlappingSegmentsStats segmentsStats) {
            this.forwardPath = forwardPath;
            this.reversePath = reversePath;
            this.segmentsStats = segmentsStats;
        }

    }
}
