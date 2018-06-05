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

package org.openkilda.atdd.staging.service.flowcalculator;

import static java.lang.String.format;

import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.topology.TopologyEngineService;
import org.openkilda.atdd.staging.steps.helpers.FlowSetBuilder;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.payload.flow.FlowPayload;

import org.junit.Assume;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FlowManagerImpl implements FlowManager {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("ddMMMHHmm");

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TopologyEngineService topologyEngineService;

    /**
     * Creates a number of flows with given number of alternate paths and at least 1 a-switch ISL. a-switch ISL
     * will later allow to bring such link down and verify the further flow behavior. <br>
     * Note that unlike some other methods here, the flow(s) will already be created in the system.
     *
     * @param flowsAmount    amount of flows to create. Will throw assumption error if unable to find enough flows in
     *                       given topology
     * @param alternatePaths amount of alternate paths that should be available for the created flows
     * @param bandwidth             bandwidth for created flows
     * @return map. Key: created flow. Value: list of a-switch isls for flow.
     */
    @Override
    public Map<FlowPayload, List<TopologyDefinition.Isl>> createFlowsWithASwitch(int flowsAmount,
                                                                                 int alternatePaths, int bandwidth) {

        final List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        FlowSetBuilder flowSet = new FlowSetBuilder();
        boolean foundEnoughFlows = false;
        Map<FlowPayload, List<TopologyDefinition.Isl>> result = new HashMap<>();

        for (TopologyDefinition.Switch srcSwitch : switches) {
            for (TopologyDefinition.Switch dstSwitch : switches) {
                if (srcSwitch.getDpId().compareTo(dstSwitch.getDpId()) >= 0) {
                    continue;
                }
                // test only bi-directional flow
                List<PathInfoData> forwardPaths = topologyEngineService
                        .getPaths(srcSwitch.getDpId(), dstSwitch.getDpId());
                List<PathInfoData> reversePaths = topologyEngineService
                        .getPaths(dstSwitch.getDpId(), srcSwitch.getDpId());
                boolean hasAlternatePath = forwardPaths.size() > alternatePaths && reversePaths.size() > alternatePaths;
                if (hasAlternatePath) {
                    //try creating flow to see the actual path being used
                    String flowId = format("%s-%s-%s", srcSwitch.getName(), dstSwitch.getName(),
                            sdf.format(new Date()));
                    FlowPayload flow = flowSet.buildWithAnyPortsInUniqueVlan(flowId, srcSwitch, dstSwitch, bandwidth);
                    northboundService.addFlow(flow);
                    List<PathNode> path = northboundService.getFlowPath(flowId).getPath().getPath();
                    List<TopologyDefinition.Isl> isls = new ArrayList<>();
                    for (int i = 1; i < path.size(); i += 2) {
                        PathNode from = path.get(i - 1);
                        PathNode to = path.get(i);
                        isls.addAll(topologyDefinition.getIslsForActiveSwitches().stream().filter(isl ->
                                isl.getSrcSwitch().getDpId().equals(from.getSwitchId())
                                        && isl.getDstSwitch().getDpId().equals(to.getSwitchId())
                                        && isl.getAswitch() != null).collect(Collectors.toList()));
                    }
                    if (isls.isEmpty()) { //created flow has no aswitch links, doesn't work for us
                        northboundService.deleteFlow(flowId);
                    } else {
                        result.put(flow, isls);
                    }
                    foundEnoughFlows = result.size() == flowsAmount;
                }
                if (foundEnoughFlows) {
                    break;
                }
            }
            if (foundEnoughFlows) {
                break;
            }
        }
        if (!foundEnoughFlows) {
            result.keySet().forEach(f -> northboundService.deleteFlow(f.getId()));
        }
        Assume.assumeTrue("Didn't find enough of requested flows. This test cannot be run on given topology",
                foundEnoughFlows);
        return result;
    }

    /**
     * Returns all available flows for all 'active' switches in the topology.
     */
    @Override
    public Set<FlowPayload> allActiveSwitchesFlows() {

        FlowSetBuilder builder = new FlowSetBuilder();

        final List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        // check each combination of active switches for a path between them and create
        // a flow definition if the path exists
        switches.forEach(srcSwitch ->
                switches.forEach(dstSwitch -> {
                    // skip the same switch flow and reverse combination of switches
                    if (srcSwitch.getDpId().compareTo(dstSwitch.getDpId()) >= 0) {
                        return;
                    }

                    // test only bi-directional flow
                    List<PathInfoData> forwardPath = topologyEngineService
                            .getPaths(srcSwitch.getDpId(), dstSwitch.getDpId());
                    List<PathInfoData> reversePath = topologyEngineService
                            .getPaths(dstSwitch.getDpId(), srcSwitch.getDpId());
                    if (!forwardPath.isEmpty() && !reversePath.isEmpty()) {
                        String flowId = format("%s-%s", srcSwitch.getName(), dstSwitch.getName());
                        builder.addFlow(flowId, srcSwitch, dstSwitch);
                    }
                })
        );

        return builder.getFlows();
    }

    /**
     * Returns all available flows for all active traffic generators in the topology. Note that active generator
     * should also be connected to active switch
     */
    @Override
    public Set<FlowPayload> allActiveTraffgenFlows() {

        FlowSetBuilder builder = new FlowSetBuilder();

        final List<TopologyDefinition.TraffGen> traffGens = topologyDefinition.getActiveTraffGens();
        // check each combination of active traffGens and create a flow definition
        traffGens.forEach(srcTraffGen -> {
            TopologyDefinition.Switch srcSwitch = srcTraffGen.getSwitchConnected();
            traffGens.forEach(dstTraffGen -> {
                TopologyDefinition.Switch dstSwitch = dstTraffGen.getSwitchConnected();
                // skip the same switch flow and reverse combination of switches
                if (srcSwitch.getDpId().compareTo(dstSwitch.getDpId()) >= 0) {
                    return;
                }

                String flowId = format("%s-%s", srcSwitch.getName(), dstSwitch.getName());
                builder.addFlow(flowId, srcSwitch, srcTraffGen.getSwitchPort(), dstSwitch, dstTraffGen.getSwitchPort());
            });
        });
        return builder.getFlows();
    }
}
