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

import org.openkilda.model.FlowEncapsulationType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

/**
 * Describes criteria for delete rules action.
 */
@Value
@Builder
public class DeleteRulesCriteria implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("cookie")
    Long cookie;

    @JsonProperty("in_port")
    Integer inPort;
    @JsonProperty("encapsulation_id")
    Integer encapsulationId;

    @JsonProperty("priority")
    Integer priority;

    @JsonProperty("out_port")
    Integer outPort;

    @JsonProperty("encapsulation_type")
    FlowEncapsulationType encapsulationType;

    @JsonCreator
    public DeleteRulesCriteria(
            @JsonProperty("cookie") Long cookie,
            @JsonProperty("in_port") Integer inPort,
            @JsonProperty("encapsulation_id") Integer encapsulationId,
            @JsonProperty("priority") Integer priority,
            @JsonProperty("out_port") Integer outPort,
            @JsonProperty("encapsulation_type") FlowEncapsulationType encapsulationType) {
        if ((cookie == null || cookie == 0)
                && (inPort == null || inPort == 0)
                && (encapsulationId == null || encapsulationId == 0)
                && (priority == null || priority == 0)
                && (outPort == null || outPort == 0)
                && (encapsulationType == null)) {
            throw new IllegalArgumentException("DeleteRulesCriteria can't be constructed empty.");
        }

        this.cookie = cookie;
        this.inPort = inPort;
        this.encapsulationId = encapsulationId;
        this.priority = priority;
        this.outPort = outPort;
        this.encapsulationType = encapsulationType;
    }
}

