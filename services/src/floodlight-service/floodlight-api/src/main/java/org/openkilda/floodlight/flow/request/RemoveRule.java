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

import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.flow.RuleType;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RemoveRule extends SpeakerFlowRequest {
    @JsonProperty("cookie")
    private Cookie cookie;

    @JsonProperty("criteria")
    private DeleteRulesCriteria criteria;

    @JsonProperty("meter_id")
    private MeterId meterId;

    @JsonProperty("rule_type")
    private RuleType ruleType;

    @JsonCreator
    @Builder
    public RemoveRule(@JsonProperty("message_context") MessageContext messageContext,
                      @JsonProperty("command_id") UUID commandId,
                      @JsonProperty(FLOW_ID) final String flowId,
                      @JsonProperty("switch_id") final SwitchId switchId,
                      @JsonProperty("cookie") final Cookie cookie,
                      @JsonProperty("criteria") DeleteRulesCriteria criteria,
                      @JsonProperty("meter_id") MeterId meterId,
                      @JsonProperty("multi_table") boolean multiTable,
                      @JsonProperty("rule_type") RuleType ruleType) {
        super(messageContext, commandId, flowId, switchId, multiTable);
        this.criteria = criteria;
        this.cookie = cookie;
        this.meterId = meterId;
        this.ruleType = ruleType;
    }
}
