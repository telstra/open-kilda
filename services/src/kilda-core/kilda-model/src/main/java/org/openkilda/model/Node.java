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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Defines the payload payload of a Message representing a path node info.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"segmentLatency", "cookie"})
@Builder
public class Node implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("port_no")
    private int portNo;

    @JsonProperty("seq_id")
    private int seqId;

    @JsonProperty("segment_latency")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long segmentLatency;

    @JsonProperty("cookie")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) // Needed to exclude when not set
    private Long cookie;
}
