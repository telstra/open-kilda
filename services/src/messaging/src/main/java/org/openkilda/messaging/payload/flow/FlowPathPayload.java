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

package org.openkilda.messaging.payload.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Flow path representation class.
 */
// TODO move into api module
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowPathPayload implements Serializable {
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

    @JsonProperty("protected_path")
    protected FlowProtectedPath protectedPath;

    /**
     * The information about other flows in diversity group and intersection stats with primary flow.
     */
    @JsonProperty("diverse_group")
    protected DiverseGroupPayload diverseGroupPayload;

    /**
     * The information about other flows in diversity group and intersection stats with protected flow.
     */
    @JsonProperty("diverse_group_protected")
    protected DiverseGroupPayload diverseGroupProtectedPayload;

    @Builder
    @JsonCreator
    public FlowPathPayload(@JsonProperty("flowid") String id,
                           @JsonProperty("flowpath_forward") List<PathNodePayload> forwardPath,
                           @JsonProperty("flowpath_reverse") List<PathNodePayload> reversePath,
                           @JsonProperty("protected_path") FlowProtectedPath protectedPath,
                           @JsonProperty("diverse_group") DiverseGroupPayload diverseGroupPayload,
                           @JsonProperty("diverse_group_protected") DiverseGroupPayload diverseGroupProtectedPayload) {
        this.id = id;
        this.forwardPath = forwardPath;
        this.reversePath = reversePath;
        this.protectedPath = protectedPath;
        this.diverseGroupPayload = diverseGroupPayload;
        this.diverseGroupProtectedPayload = diverseGroupProtectedPayload;
    }

    @Data
    @NoArgsConstructor
    public static class FlowProtectedPath implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("flowpath_forward")
        private List<PathNodePayload> forwardPath;

        @JsonProperty("flowpath_reverse")
        private List<PathNodePayload> reversePath;

        @Builder
        @JsonCreator
        public FlowProtectedPath(@JsonProperty("flowpath_forward") List<PathNodePayload> forwardPath,
                                 @JsonProperty("flowpath_reverse") List<PathNodePayload> reversePath) {
            this.forwardPath = forwardPath;
            this.reversePath = reversePath;
        }
    }
}
