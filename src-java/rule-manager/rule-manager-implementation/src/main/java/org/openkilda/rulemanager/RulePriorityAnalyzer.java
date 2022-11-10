/* Copyright 2022 Telstra Open Source
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

import org.openkilda.rulemanager.Constants.Priority;

public final class RulePriorityAnalyzer {
    /**
     * Check is the priority value match the priority of generic flow endpoint rules.
     */
    public static boolean isGenericFlowEndpoint(int priority) {
        return priority == Priority.FLOW_PRIORITY
                || priority == Priority.DEFAULT_FLOW_PRIORITY
                || priority == Priority.DOUBLE_VLAN_FLOW_PRIORITY;
    }

    private RulePriorityAnalyzer() {
        // deny object creation
    }
}
