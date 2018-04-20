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

package org.openkilda.messaging.command.switches;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Describes criteria for delete rules action.
 */
@Value
@Builder
public class DeleteRulesCriteria {

    @JsonProperty("cookie")
    Long cookie;

    @JsonProperty("in_port")
    Integer inPort;
    @JsonProperty("in_vlan")
    Integer inVlan;

    @JsonProperty("priority")
    Integer priority;

    @JsonProperty("out_vlan")
    Integer outPort;

    @JsonCreator
    public DeleteRulesCriteria(
            @JsonProperty("cookie") Long cookie,
            @JsonProperty("in_port") Integer inPort,
            @JsonProperty("in_vlan") Integer inVlan,
            @JsonProperty("priority") Integer priority,
            @JsonProperty("out_vlan") Integer outPort) {
        if ((cookie == null || cookie == 0)
                && (inPort == null || inPort == 0)
                && (inVlan == null || inVlan == 0)
                && (priority == null || priority == 0)
                && (outPort == null || outPort == 0)) {
            throw new IllegalArgumentException("DeleteRulesCriteria can't be constructed empty.");
        }

        this.cookie = cookie;
        this.inPort = inPort;
        this.inVlan = inVlan;
        this.priority = priority;
        this.outPort = outPort;
    }
}

