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
import org.openkilda.messaging.model.SwapFlowDto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents bulk update flow northbound request.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class SwapFlowEndpointRequest extends CommandData {
    private static final long serialVersionUID = 1L;

    @NonNull
    @JsonProperty("first_flow")
    private SwapFlowDto firstFlow;

    @NonNull
    @JsonProperty("second_flow")
    private SwapFlowDto secondFlow;

    public SwapFlowEndpointRequest(@JsonProperty("first_flow") SwapFlowDto firstFlow,
                                   @JsonProperty("second_flow") SwapFlowDto secondFlow) {
        this.firstFlow = firstFlow;
        this.secondFlow = secondFlow;
    }
}
