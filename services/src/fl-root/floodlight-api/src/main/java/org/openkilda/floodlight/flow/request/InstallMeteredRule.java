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

package org.openkilda.floodlight.flow.request;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class InstallMeteredRule extends InstallFlow {

    /**
     * Allocated meter id.
     */
    @JsonProperty("meter_id")
    private final Long meterId;

    /**
     * Flow bandwidth value.
     */
    @JsonProperty("bandwidth")
    private final Long bandwidth;

    public InstallMeteredRule(MessageContext messageContext, String commandId, String flowId, Long cookie,
                              SwitchId switchId, Integer inputPort, Integer outputPort, Long meterId, Long bandwidth) {
        super(messageContext, commandId, flowId, cookie, switchId, inputPort, outputPort);
        this.meterId = meterId;
        this.bandwidth = bandwidth;
    }
}
