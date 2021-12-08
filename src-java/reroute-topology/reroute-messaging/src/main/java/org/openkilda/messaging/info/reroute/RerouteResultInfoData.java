/* Copyright 2021 Telstra Open Source
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

package org.openkilda.messaging.info.reroute;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.reroute.error.RerouteError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Builder
@Data
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerouteResultInfoData extends InfoData {

    @JsonProperty("flow_id")
    private String flowId;
    @JsonProperty("success")
    private boolean success;
    @JsonProperty("reroute_error")
    private RerouteError rerouteError;
    @JsonProperty("is_y_flow")
    private boolean yFlow;

    @JsonCreator
    public RerouteResultInfoData(@JsonProperty("flow_id") String flowId,
                                 @JsonProperty("success") boolean success,
                                 @JsonProperty("reroute_error") RerouteError rerouteError,
                                 @JsonProperty("is_y_flow") boolean yFlow) {
        this.flowId = flowId;
        this.success = success;
        this.rerouteError = rerouteError;
        this.yFlow = yFlow;
    }
}
