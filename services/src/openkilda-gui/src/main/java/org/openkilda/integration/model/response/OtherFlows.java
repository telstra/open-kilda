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

package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class OtherFlows implements Serializable {

    private static final long serialVersionUID = 5279827724956688650L;

    @JsonProperty("flowid")
    private String flowId;

    @JsonProperty("flowpath_forward")
    private List<FlowPathNode> forward;
    
    @JsonProperty("flowpath_reverse")
    private List<FlowPathNode> reverse;

    @JsonProperty("overlapping_segments")
    private OverlappingSegments overlappingSegments;
    
    @JsonProperty("protected_path")
    private FlowProtectedPath protectedPath;

}
