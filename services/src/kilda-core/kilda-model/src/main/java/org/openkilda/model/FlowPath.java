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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.Serializable;
import java.util.List;

/**
 * Representation of a flow path. As opposed to flow segment entity which serves as a mark for ISLs used by the flow,
 * this is switch-based and keeps the list of switch-port pairs the flow path goes through.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"latency", "minAvailableBandwidth"})
@Builder
public class FlowPath implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Latency value in nseconds.
     */
    @JsonProperty("latency_ns")
    private long latency;

    @JsonProperty("min_available_bandwidth")
    private Long minAvailableBandwidth;

    @NonNull
    @JsonProperty("path")
    private List<Node> nodes;

    /**
     * Needed to support old storage schema.
     *
     * @deprecated Must be removed along with {@link Flow#flowPath}.
     */
    @Deprecated
    @JsonInclude(Include.NON_NULL)
    private Long timestamp;

    /**
     * A node of a flow path ({@link FlowPath}).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(exclude = {"segmentLatency", "cookie"})
    @Builder
    public static class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("switch_id")
        private SwitchId switchId;

        @JsonProperty("port_no")
        private int portNo;

        /**
         * Needed to support old storage schema.
         *
         * @deprecated Must be removed along with {@link Flow#flowPath}.
         */
        @Deprecated
        @JsonProperty("seq_id")
        private int seqId;

        @JsonProperty("segment_latency")
        @JsonInclude(Include.NON_NULL)
        private Long segmentLatency;

        /**
         * Needed to support old storage schema.
         *
         * @deprecated Must be removed along with {@link Flow#flowPath}.
         */
        @Deprecated
        @JsonProperty("cookie")
        @JsonInclude(Include.NON_DEFAULT) // Needed to exclude when not set
        private Long cookie;
    }
}
