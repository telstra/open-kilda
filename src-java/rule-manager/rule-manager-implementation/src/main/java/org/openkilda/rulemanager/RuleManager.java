/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager;

import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.SwitchId;

import java.util.List;

/**
 * Represents RuleManger lib API.
 */
public interface RuleManager {

    /**
     * Builds all required rules, meters and groups for flow path.
     *
     * @param flowPath target flow path
     * @param filterOutUsedSharedRules if False - all path shared rules (QinQ, server42 QinQ, LLDP input, ARP input)
     *                                 will be included in result list.
     *                                 If True - path shared rule will be included in result list only if this
     *                                 rule is NOT used by any other overlapping path.
     *                                 Overlapping means different for different rules:
     *                                 * LLDP input, ARP input - shared rule use same switch port
     *                                 * QinQ, server42 QinQ - shared rule use same switch port and same outer vlan
     * @param adapter adapter with all needed data. All overlapping paths and flows for parameter
     *                filterOutUsedSharedRules must be presented in this adapter
     * @return list of rules, meters and groups.
     */
    List<SpeakerData> buildRulesForFlowPath(FlowPath flowPath, boolean filterOutUsedSharedRules,
                                            DataAdapter adapter);

    /**
     * Build all required rules, meters and groups for switch. Including service and all required flow-related rules.
     */
    List<SpeakerData> buildRulesForSwitch(SwitchId switchId, DataAdapter adapter);

    /**
     * Build all required rules and meters y-flow.
     */
    List<SpeakerData> buildRulesForYFlow(List<FlowPath> flowPaths, DataAdapter adapter);

    /**
     * Build all required service rules for ISL on specified port.
     */
    List<SpeakerData> buildIslServiceRules(SwitchId switchId, int port, DataAdapter adapter);

    List<SpeakerData> buildLacpRules(SwitchId switchId, int logicalPort, DataAdapter adapter);

    List<SpeakerData> buildMirrorPointRules(FlowMirrorPoints mirrorPoints, DataAdapter adapter);

    /**
     * Builds all required rules, meters and groups for flow path.
     *
     * @param haPath target ha-flow path
     * @param filterOutUsedSharedRules if False - all path shared rules (QinQ, server42 QinQ, etc.)
     *                                 will be included in result list.
     *                                 If True - path shared rule will be included in result list only if this
     *                                 rule is NOT used by any other overlapping path.
     * @param adapter adapter with all needed data. All overlapping paths and flows for parameter
     *                filterOutUsedSharedRules must be presented in this adapter
     * @return list of rules, meters and groups.
     */
    List<SpeakerData> buildRulesHaFlowPath(HaFlowPath haPath, boolean filterOutUsedSharedRules, DataAdapter adapter);
}
