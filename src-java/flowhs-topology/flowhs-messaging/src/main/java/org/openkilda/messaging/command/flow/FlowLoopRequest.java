/* Copyright 2020 Telstra Open Source
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

import org.openkilda.messaging.nbtopology.request.BaseRequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * Represents flow loop northbound request.
 */
@EqualsAndHashCode(callSuper = false)
@Data()
public abstract class FlowLoopRequest extends BaseRequest {
    private static final long serialVersionUID = 663188759544246503L;

    @NonNull
    @JsonProperty("flow_id")
    String flowId;

    public FlowLoopRequest(@JsonProperty("flow_id") String flowId) {
        this.flowId = flowId;
    }
}

