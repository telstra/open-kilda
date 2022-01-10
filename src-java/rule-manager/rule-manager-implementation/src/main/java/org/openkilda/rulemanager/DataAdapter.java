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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.YFlow;

import java.util.Map;
import java.util.Set;

public interface DataAdapter {

    Map<PathId, FlowPath> getFlowPaths();

    Flow getFlow(PathId pathId);

    FlowTransitEncapsulation getTransitEncapsulation(PathId pathId, PathId oppositePathId);

    Switch getSwitch(SwitchId switchId);

    SwitchProperties getSwitchProperties(SwitchId switchId);

    KildaFeatureToggles getFeatureToggles();

    Set<Integer> getSwitchIslPorts(SwitchId switchId);

    YFlow getYFlow(PathId pathId);
}
