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

import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;

import java.util.List;

/**
 * Represents RuleManger lib API.
 */
public interface RuleManager {

    List<SpeakerCommandData> buildRulesForFlowPath(FlowPath flowPath, DataAdapter adapter);

    /**
     * Build all required rules, meters and groups for switch. Including service and all required flow-related rules.
     */
    List<SpeakerCommandData> buildRulesForSwitch(SwitchId switchId, DataAdapter adapter);
}
