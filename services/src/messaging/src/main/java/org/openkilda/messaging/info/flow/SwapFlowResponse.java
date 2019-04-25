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
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;

import java.util.Objects;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@EqualsAndHashCode
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

    /**
     * Gets a first flow response.
     *
     * @return the first flow response.
     */
    public FlowResponse getFirstFlow() {
        return firstFlow;
    }

    /**
     * Sets a first flow response.
     *
     * @param firstFlow a flow response instance.
     */
    public void setFirstFlow(FlowResponse firstFlow) {
        this.firstFlow = firstFlow;
    }

    /**
     * Gets a second flow response.
     *
     * @return the second flow response.
     */
    public FlowResponse getSecondFlow() {
        return secondFlow;
    }

    /**
     * Sets a second flow response.
     *
     * @param secondFlow a flow response instance.
     */
    public void setSecondFlow(FlowResponse secondFlow) {
        this.secondFlow = secondFlow;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SwapFlowResponse response = (SwapFlowResponse) o;
        return Objects.equals(firstFlow, response.firstFlow) && Objects.equals(secondFlow, response.secondFlow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), firstFlow, secondFlow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SwapFlowResponse{" + "firstFlow=" + firstFlow + ", secondFlow=" + secondFlow + '}';
    }
}
