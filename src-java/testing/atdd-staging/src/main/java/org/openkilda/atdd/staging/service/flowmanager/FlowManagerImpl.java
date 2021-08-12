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

package org.openkilda.atdd.staging.service.flowmanager;

import static java.lang.String.format;

import org.openkilda.atdd.staging.helpers.FlowSet;
import org.openkilda.atdd.staging.helpers.FlowSet.FlowBuilder;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.database.Database;
import org.openkilda.testing.service.northbound.NorthboundService;

import org.junit.Assume;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class helps to define/create different flow sets that may be required by tests.
 */
@Service
public class FlowManagerImpl implements FlowManager {
    private final SimpleDateFormat sdf;

    @Autowired @Qualifier("northboundServiceImpl")
    private NorthboundService northboundService;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private Database db;

    public FlowManagerImpl() {
        this.sdf = new SimpleDateFormat("ddMMMHHmm", Locale.US);
    }

    /**
     * Creates a number of flows with given number of alternate paths and at least 1 a-switch ISL. a-switch ISL
     * will later allow to bring such link down and verify the further flow behavior. <br>
     * Note that unlike some other methods here, the flow(s) will already be created in the system.
     *
     * @param flowsAmount amount of flows to create. Will throw assumption error if unable to find enough flows
     * @param alternatePaths amount of alternate paths that should be available for the created flows
     * @param bandwidth bandwidth for created flows
     * @return map. Key: created flow. Value: list of a-switch isls for flow.
     */
    @Override
    public Map<FlowPayload, List<TopologyDefinition.Isl>> createFlowsWithASwitch(int flowsAmount,
                                                                                 int alternatePaths, int bandwidth) {

        final List<TopologyDefinition.TraffGen> traffGens = topologyDefinition.getActiveTraffGens();
        FlowSet flowSet = new FlowSet();
        boolean foundEnoughFlows = false;
        Map<FlowPayload, List<TopologyDefinition.Isl>> result = new HashMap<>();

        for (TopologyDefinition.TraffGen srcTraffGen : traffGens) {
            for (TopologyDefinition.TraffGen dstTraffGen : traffGens) {
                TopologyDefinition.Switch srcSwitch = srcTraffGen.getSwitchConnected();
                TopologyDefinition.Switch dstSwitch = dstTraffGen.getSwitchConnected();
                if (srcSwitch.getDpId().compareTo(dstSwitch.getDpId()) >= 0) {
                    continue;
                }
                // test only bi-directional flow
                List<PathInfoData> forwardPaths = db
                        .getPaths(srcSwitch.getDpId(), dstSwitch.getDpId());
                List<PathInfoData> reversePaths = db
                        .getPaths(dstSwitch.getDpId(), srcSwitch.getDpId());
                boolean hasAlternatePath = forwardPaths.size() > alternatePaths && reversePaths.size() > alternatePaths;
                if (hasAlternatePath) {
                    //try creating flow to see the actual path being used
                    String flowId = format("%s_%s_%s", srcSwitch.getName(), dstSwitch.getName(),
                            sdf.format(new Date()));
                    FlowBuilder builder = flowSet.getFlowBuilder(flowId, srcSwitch, dstSwitch);
                    FlowPayload flow = builder.buildInUniqueVlan(srcTraffGen.getSwitchPort(),
                            dstTraffGen.getSwitchPort());
                    flow.setMaximumBandwidth(bandwidth);
                    northboundService.addFlow(flow);
                    List<PathNodePayload> flowPath = northboundService.getFlowPath(flowId).getForwardPath();
                    List<TopologyDefinition.Isl> isls = new ArrayList<>();
                    for (int i = 1; i < flowPath.size(); i++) {
                        PathNodePayload from = flowPath.get(i - 1);
                        PathNodePayload to = flowPath.get(i);
                        isls.addAll(topologyDefinition.getIslsForActiveSwitches().stream().filter(isl ->
                                ((isl.getSrcSwitch().getDpId().equals(from.getSwitchId())
                                        && isl.getDstSwitch().getDpId().equals(to.getSwitchId()))
                                        || (isl.getSrcSwitch().getDpId().equals(to.getSwitchId())
                                        && isl.getDstSwitch().getDpId().equals(from.getSwitchId())))
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
        Assume.assumeTrue("Didn't find enough of requested flows. This test cannot be run on given topology. "
                + "Do you have enough a-switch links in the topology?", foundEnoughFlows);
        return result;
    }

    /**
     * Returns all available flows for all 'active' switches in the topology.
     */
    @Override
    public Set<FlowPayload> allActiveSwitchesFlows() {

        FlowSet flowSet = new FlowSet();

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
                    List<PathInfoData> forwardPath = db
                            .getPaths(srcSwitch.getDpId(), dstSwitch.getDpId());
                    List<PathInfoData> reversePath = db
                            .getPaths(dstSwitch.getDpId(), srcSwitch.getDpId());
                    if (!forwardPath.isEmpty() && !reversePath.isEmpty()) {
                        String flowId = format("%s-%s", srcSwitch.getName(), dstSwitch.getName());
                        flowSet.addFlow(flowId, srcSwitch, dstSwitch);
                    }
                })
        );

        return flowSet.getFlows();
    }

    /**
     * Returns all available flows for all active traffic generators in the topology. Note that active generator
     * should also be connected to active switch
     */
    @Override
    public Set<FlowPayload> allActiveTraffgenFlows() {

        FlowSet flowSet = new FlowSet();

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
                flowSet.addFlow(flowId, srcSwitch, srcTraffGen.getSwitchPort(), dstSwitch, dstTraffGen.getSwitchPort());
            });
        });
        return flowSet.getFlows();
    }

    @Override
    public FlowPayload randomFlow() {
        FlowSet flowSet = new FlowSet();
        Random r = new Random();
        List<Switch> activeSwitches = topologyDefinition.getActiveSwitches();
        Switch srcSwitch = activeSwitches.get(r.nextInt(activeSwitches.size()));
        activeSwitches = activeSwitches.stream()
                .filter(s -> !s.equals(srcSwitch))
                .collect(Collectors.toList());
        Switch dstSwitch = activeSwitches.get(r.nextInt(activeSwitches.size()));
        String flowId = format("%s-%s-%s", srcSwitch.getName(), dstSwitch.getName(),
                sdf.format(new Date()));
        return flowSet.buildWithAnyPortsInUniqueVlan(flowId, srcSwitch, dstSwitch, 1000);
    }
}
