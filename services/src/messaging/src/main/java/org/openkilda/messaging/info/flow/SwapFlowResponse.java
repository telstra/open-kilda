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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = false)
public class SwapFlowResponse extends InfoData {

    private static final long serialVersionUID = 8858971052045302274L;

    @JsonProperty("first_flow")
    private FlowResponse firstFlow;

    @JsonProperty("second_flow")
    private FlowResponse secondFlow;

    /**
     * Constructs an instance.
     *
     * @param firstFlow first flow to swap.
     * @param secondFlow second flow to swap.
     */
    @JsonCreator
    public SwapFlowResponse(@JsonProperty("first_flow") FlowResponse firstFlow,
                            @JsonProperty("second_flow") FlowResponse secondFlow) {
        this.firstFlow = firstFlow;
        this.secondFlow = secondFlow;
    }
}
