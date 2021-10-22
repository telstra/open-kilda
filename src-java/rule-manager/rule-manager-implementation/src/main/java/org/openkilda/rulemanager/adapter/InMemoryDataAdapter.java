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

package org.openkilda.rulemanager.adapter;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.rulemanager.DataAdapter;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class InMemoryDataAdapter implements DataAdapter {

    Map<PathId, FlowPath> flowPaths;
    Map<PathId, Flow> flows;
    Map<PathId, FlowTransitEncapsulation> transitEncapsulations;
    Map<SwitchId, Switch> switches;
    Map<SwitchId, SwitchProperties> switchProperties;

    @Override
    public Flow getFlow(PathId pathId) {
        Flow flow = flows.get(pathId);
        if (flow == null) {
            throw new IllegalStateException(format("Flow for pathId '%s' not found.", pathId));
        }
        return flow;
    }

    @Override
    public Switch getSwitch(SwitchId switchId) {
        Switch sw = switches.get(switchId);
        if (sw == null) {
            throw new IllegalStateException(format("Switch '%s' not found.", switchId));
        }
        return sw;
    }

    @Override
    public SwitchProperties getSwitchProperties(SwitchId switchId) {
        SwitchProperties result = switchProperties.get(switchId);
        if (result == null) {
            throw new IllegalStateException(format("Switch properties for '%s' not found.", switchId));
        }
        return result;
    }

    @Override
    public FlowTransitEncapsulation getTransitEncapsulation(PathId pathId) {
        FlowTransitEncapsulation result = transitEncapsulations.get(pathId);
        if (result == null) {
            throw new IllegalArgumentException(format("Transit encapsulation for path id '%s' not found.", pathId));
        }
        return result;
    }
}
