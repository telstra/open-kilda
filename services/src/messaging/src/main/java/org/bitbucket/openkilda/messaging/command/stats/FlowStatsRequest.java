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

package org.bitbucket.openkilda.messaging.command.stats;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowStatsRequest extends CommandData {
    /**
     * The flow id to request stats for. It is a mandatory parameter.
     */
    @JsonProperty("switch_id")
    protected String flowId;

    /**
     * Constructs statistics request.
     *
     * @param flowId switch id
     */
    @JsonCreator
    public FlowStatsRequest(@JsonProperty("switch_id") final String flowId) {
        setFlowId(flowId);
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Sets switch id.
     *
     * @param flowId switch id
     */
    public void setFlowId(String flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(flowId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.flowId = flowId;
    }
}
