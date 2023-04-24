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
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.YFlow;
import org.openkilda.rulemanager.DataAdapter;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class InMemoryDataAdapter implements DataAdapter {

    Map<PathId, FlowPath> commonFlowPaths;
    Map<PathId, FlowPath> haFlowSubPaths;
    Map<PathId, Flow> flows;
    Map<PathId, FlowTransitEncapsulation> transitEncapsulations;
    Map<SwitchId, Switch> switches;
    Map<SwitchId, SwitchProperties> switchProperties;
    Map<SwitchId, Set<Integer>> switchIslPorts;
    Map<SwitchId, List<LagLogicalPort>> switchLagPorts;
    KildaFeatureToggles featureToggles;
    Map<PathId, YFlow> yFlows;
    Map<PathId, HaFlow> haFlowMap;
    Map<PathId, HaFlowPath> haFlowPathMap;

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
    public YFlow getYFlow(PathId pathId) {
        YFlow yFlow = yFlows.get(pathId);
        if (yFlow == null) {
            throw new IllegalStateException(format("Y-flow for pathId '%s' not found.", pathId));
        }
        return yFlow;
    }

    @Override
    public FlowTransitEncapsulation getTransitEncapsulation(PathId pathId, PathId oppositePathId) {
        FlowTransitEncapsulation result = transitEncapsulations.get(pathId);
        if (result == null) {
            result = transitEncapsulations.get(oppositePathId);
        }
        if (result == null) {
            throw new IllegalArgumentException(format("Transit encapsulation for path id '%s' not found.", pathId));
        }
        return result;
    }

    @Override
    public Set<Integer> getSwitchIslPorts(SwitchId switchId) {
        Set<Integer> result = switchIslPorts.get(switchId);
        if (result == null) {
            throw new IllegalStateException(format("Switch isl ports for '%s' not found.", switchId));
        }
        return result;
    }

    @Override
    public List<LagLogicalPort> getLagLogicalPorts(SwitchId switchId) {
        List<LagLogicalPort> result = switchLagPorts.getOrDefault(switchId, Collections.emptyList());
        if (result == null) {
            throw new IllegalStateException(format("Switch lag ports for '%s' not found.", switchId));
        }
        return result;
    }

    @Override
    public HaFlow getHaFlow(PathId pathId) {
        HaFlow haFlow = haFlowMap.get(pathId);
        if (haFlow == null) {
            throw new IllegalStateException(format("Ha-flow for pathId '%s' not found.", pathId));
        }
        return haFlow;
    }

    @Override
    public HaFlowPath getHaFlowPath(PathId haFlowPathId) {
        HaFlowPath haFlowPath = haFlowPathMap.get(haFlowPathId);
        if (haFlowPath == null) {
            throw new IllegalStateException(format("Ha-flow path '%s' not found.", haFlowPath));
        }
        return haFlowPath;
    }
}
