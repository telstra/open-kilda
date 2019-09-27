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

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwitchRulesInstallRequest extends CommandData {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("install_rules")
    private InstallRulesAction installRulesAction;

    @JsonProperty("multi_table")
    private boolean multiTable = false;

    @JsonProperty("isl_ports")
    private List<Integer> islPorts = new ArrayList<>();

    @JsonProperty("flow_ports")
    private List<Integer> flowPorts = new ArrayList<>();

    /**
     * Constructs an install switch rules request.
     *
     * @param switchId switch id to install rules on.
     * @param installRulesAction defines what to do about the default rules
     */
    @JsonCreator
    public SwitchRulesInstallRequest(
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("install_rules") InstallRulesAction installRulesAction
    ) {
        this.switchId = Objects.requireNonNull(switchId, "switch_id must not be null");
        if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("switch_id has invalid value");
        }

        this.installRulesAction = Objects.requireNonNull(installRulesAction);
    }

    public SwitchId getSwitchId() {
        return switchId;
    }

    public InstallRulesAction getInstallRulesAction() {
        return installRulesAction;
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

    public void setMultiTable(boolean multiTable) {
        this.multiTable = multiTable;
    }

    public void setIslPorts(List<Integer> islPorts) {
        this.islPorts = islPorts;
    }

    public void setFlowPorts(List<Integer> flowPorts) {
        this.flowPorts = flowPorts;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add("switch_id", switchId)
                .add("install_rules", installRulesAction)
                .toString();
    }
}
