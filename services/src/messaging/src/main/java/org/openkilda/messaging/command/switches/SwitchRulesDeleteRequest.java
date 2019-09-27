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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwitchRulesDeleteRequest extends CommandData {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("delete_rules")
    private DeleteRulesAction deleteRulesAction;

    /**
     * Used for deleting specific switch rule .. doesn't have to be a default rule.
     */
    @JsonProperty("criteria")
    private DeleteRulesCriteria criteria;

    @JsonProperty("multi_table")
    private boolean multiTable = false;

    @JsonProperty("isl_ports")
    private List<Integer> islPorts = new ArrayList<>();

    @JsonProperty("flow_ports")
    private List<Integer> flowPorts = new ArrayList<>();

    /**
     * Constructs a delete switch rules request.
     *
     * @param switchId switch id to delete rules from.
     * @param deleteRulesAction defines what to do about the default rules
     * @param criteria criteria to delete a specific rule.
     */
    @JsonCreator
    public SwitchRulesDeleteRequest(
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("delete_rules") DeleteRulesAction deleteRulesAction,
            @JsonProperty("criteria") DeleteRulesCriteria criteria
    ) {
        this.switchId = Objects.requireNonNull(switchId, "switch_id must not be null");
        if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("switch_id has invalid value");
        }

        this.deleteRulesAction = deleteRulesAction;
        // NB: criteria is only needed if deleteRulesAction is not provided
        this.criteria = deleteRulesAction == null ? Objects.requireNonNull(criteria) : criteria;
    }

    public boolean isMultiTable() {
        return multiTable;
    }

    public List<Integer> getIslPorts() {
        return islPorts;
    }

    public List<Integer> getFlowPorts() {
        return flowPorts;
    }
}
