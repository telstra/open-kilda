/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.bolt.producer;

import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlowPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildSegments;

import org.openkilda.messaging.model.FlowDirection;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.topology.ping.model.PingContext;
import org.openkilda.wfm.topology.ping.model.PingContext.Kinds;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HaPingContextProducerTest {
    private static final String HA_FLOW_ID_1 = "test_ha_flow_1";
    private static final PathId SUB_PATH_ID_4 = new PathId("sub_path_id_4");
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    private static final PathId PATH_ID_1 = new PathId("path_1");
    private static final PathId PATH_ID_2 = new PathId("path_2");
    private static final String SUB_FLOW_ID_A = "test_ha_flow_1-a";
    private static final String SUB_FLOW_ID_B = "test_ha_flow_1-b";
    private static final String DESCRIPTION_1 = "description_1";
    private static final String DESCRIPTION_2 = "description_2";
    private static final PathId SUB_PATH_ID_1 = new PathId("sub_path_id_1");
    private static final PathId SUB_PATH_ID_2 = new PathId("sub_path_id_2");
    private static final PathId SUB_PATH_ID_3 = new PathId("sub_path_id_3");
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int VLAN_1 = 5;
    private static final int VLAN_2 = 6;
    private static final MeterId METER_ID_1 = new MeterId(11);
    private static final MeterId METER_ID_2 = new MeterId(12);

    private static final GroupId GROUP_ID_1 = new GroupId(15);

    private static final FlowSegmentCookie COOKIE_1 = FlowSegmentCookie.builder().flowEffectiveId(1)
            .direction(FlowPathDirection.FORWARD).build();
    private static final FlowSegmentCookie COOKIE_2 = FlowSegmentCookie.builder().flowEffectiveId(2)
            .direction(FlowPathDirection.REVERSE).build();
    private static final int BANDWIDTH_1 = 1000;

    private Switch sharedSwitch;
    private Switch ySwitch;
    private Switch endpointSwitchA;
    private Switch endpointSwitchB;
    private HaFlow haFlow;
    private HaSubFlow haSubFlowA;
    private HaSubFlow haSubFlowB;


    @Before
    public void setUp() {
        sharedSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        ySwitch = Switch.builder().switchId(SWITCH_ID_2).build();
        endpointSwitchA = Switch.builder().switchId(SWITCH_ID_3).build();
        endpointSwitchB = Switch.builder().switchId(SWITCH_ID_4).build();
    }

    @Test
    public void produceOk() {
        createHaFlow(false);
        final HaFlowPingContextProducer haFlowPingContextProducer = new HaFlowPingContextProducer();

        FlowTransitEncapsulation transitEncapsulation =
                new FlowTransitEncapsulation(0, FlowEncapsulationType.TRANSIT_VLAN);
        PingContext pingContext = PingContext.builder()
                .group(new org.openkilda.wfm.topology.ping.model.GroupId(4))
                .kind(Kinds.ON_DEMAND_HA_FLOW)
                .haFlow(haFlow)
                .transitEncapsulation(transitEncapsulation)
                .timeout(0L)
                .build();

        final List<PingContext> expectedPingContexts = Lists.newArrayList(
                pingContext.toBuilder()
                        .ping(new Ping(
                                new NetworkEndpoint(sharedSwitch.getSwitchId(), PORT_1),
                                new NetworkEndpoint(endpointSwitchA.getSwitchId(), PORT_1),
                                transitEncapsulation, 0
                        ))
                        .haSubFlowId(haSubFlowA.getHaSubFlowId())
                        .direction(FlowDirection.FORWARD)
                        .build(),
                pingContext.toBuilder()
                        .ping(new Ping(
                                new NetworkEndpoint(sharedSwitch.getSwitchId(), PORT_1),
                                new NetworkEndpoint(endpointSwitchB.getSwitchId(), PORT_2),
                                transitEncapsulation, 0
                        ))
                        .haSubFlowId(haSubFlowB.getHaSubFlowId())
                        .direction(FlowDirection.FORWARD)
                        .build(),
                pingContext.toBuilder()
                        .ping(new Ping(
                                new NetworkEndpoint(endpointSwitchA.getSwitchId(), PORT_1),
                                new NetworkEndpoint(sharedSwitch.getSwitchId(), PORT_1),
                                transitEncapsulation, 0
                        ))
                        .haSubFlowId(haSubFlowA.getHaSubFlowId())
                        .direction(FlowDirection.REVERSE)
                        .build(),
                pingContext.toBuilder()
                        .ping(new Ping(
                                new NetworkEndpoint(endpointSwitchB.getSwitchId(), PORT_2),
                                new NetworkEndpoint(sharedSwitch.getSwitchId(), PORT_1),
                                transitEncapsulation, 0
                        ))
                        .haSubFlowId(haSubFlowB.getHaSubFlowId())
                        .direction(FlowDirection.REVERSE)
                        .build()
        );


        List<PingContext> resultPingContexts = haFlowPingContextProducer.produce(pingContext);

        Assert.assertEquals(4, resultPingContexts.size());
        Assert.assertEquals(expectedPingContexts, resultPingContexts);
    }

    @Test
    public void produceOneHaSubFlowIsOneSwitchFlow() {

        createHaFlow(true);
        final HaFlowPingContextProducer haFlowPingContextProducer = new HaFlowPingContextProducer();

        FlowTransitEncapsulation transitEncapsulation =
                new FlowTransitEncapsulation(0, FlowEncapsulationType.TRANSIT_VLAN);
        PingContext pingContext = PingContext.builder()
                .group(new org.openkilda.wfm.topology.ping.model.GroupId(4))
                .kind(Kinds.ON_DEMAND_HA_FLOW)
                .haFlow(haFlow)
                .transitEncapsulation(transitEncapsulation)
                .timeout(0L)
                .build();

        final List<PingContext> expectedPingContexts = Lists.newArrayList(
                pingContext.toBuilder()
                        .ping(new Ping(
                                new NetworkEndpoint(sharedSwitch.getSwitchId(), PORT_1),
                                new NetworkEndpoint(endpointSwitchB.getSwitchId(), PORT_2),
                                transitEncapsulation, 0
                        ))
                        .haSubFlowId(haSubFlowB.getHaSubFlowId())
                        .direction(FlowDirection.FORWARD)
                        .build(),
                pingContext.toBuilder()
                        .ping(new Ping(
                                new NetworkEndpoint(endpointSwitchB.getSwitchId(), PORT_2),
                                new NetworkEndpoint(sharedSwitch.getSwitchId(), PORT_1),
                                transitEncapsulation, 0
                        ))
                        .haSubFlowId(haSubFlowB.getHaSubFlowId())
                        .direction(FlowDirection.REVERSE)
                        .build()
        );


        List<PingContext> resultPingContexts = haFlowPingContextProducer.produce(pingContext);

        Assert.assertEquals(2, resultPingContexts.size());
        Assert.assertEquals(expectedPingContexts, resultPingContexts);
    }

    private void createHaFlow(boolean oneIsOneSwitchFlow) {
        Switch endpointSwitchA = this.endpointSwitchA;

        if (oneIsOneSwitchFlow) {
            endpointSwitchA = sharedSwitch;
        }

        haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID_1)
                .sharedSwitch(sharedSwitch)
                .sharedPort(PORT_1)
                .sharedOuterVlan(0)
                .sharedInnerVlan(0)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        final Set<HaSubFlow> haSubFlows = new HashSet<>();
        final List<FlowPath> forwardSubPaths = new ArrayList<>();
        final List<FlowPath> reverseSubPaths = new ArrayList<>();

        final HaFlowPath pathA = createHaFlowPath(PATH_ID_1, COOKIE_1, sharedSwitch);
        final HaFlowPath pathB = createHaFlowPath(PATH_ID_2, COOKIE_2, sharedSwitch);

        haSubFlowA = buildHaSubFlow(SUB_FLOW_ID_A, endpointSwitchA, PORT_1, VLAN_1, 0, DESCRIPTION_1);
        haSubFlows.add(haSubFlowA);
        forwardSubPaths.add(createPathWithSegments(SUB_PATH_ID_1, pathA, haSubFlowA,
                Lists.newArrayList(sharedSwitch, ySwitch, endpointSwitchA)));
        reverseSubPaths.add(createPathWithSegments(SUB_PATH_ID_3, pathB, haSubFlowA,
                Lists.newArrayList(endpointSwitchA, ySwitch, sharedSwitch)));


        haSubFlowB = buildHaSubFlow(SUB_FLOW_ID_B, endpointSwitchB, PORT_2, VLAN_2, 0, DESCRIPTION_2);
        haSubFlows.add(haSubFlowB);
        forwardSubPaths.add(createPathWithSegments(SUB_PATH_ID_2, pathA, haSubFlowB,
                Lists.newArrayList(sharedSwitch, ySwitch, endpointSwitchB)));
        reverseSubPaths.add(createPathWithSegments(SUB_PATH_ID_4, pathB, haSubFlowB,
                Lists.newArrayList(endpointSwitchB, ySwitch, sharedSwitch)));

        haFlow.setHaSubFlows(haSubFlows);

        pathA.setSubPaths(forwardSubPaths);
        pathA.setHaSubFlows(haSubFlows);
        haFlow.setForwardPath(pathA);

        pathB.setSubPaths(reverseSubPaths);
        pathB.setHaSubFlows(haSubFlows);
        haFlow.setReversePath(pathB);

    }

    private HaFlowPath createHaFlowPath(PathId pathId, FlowSegmentCookie cookie, Switch sharedSwitch) {
        HaFlowPath path = buildHaFlowPath(pathId, BANDWIDTH_1, cookie, METER_ID_1, METER_ID_2, sharedSwitch,
                SWITCH_ID_2, GROUP_ID_1);
        return path;
    }

    private FlowPath createPathWithSegments(
            PathId pathId, HaFlowPath haFlowPath, HaSubFlow haSubFlow, List<Switch> switches) {
        FlowPath path = buildPath(pathId, haFlowPath, switches.get(0), switches.get(switches.size() - 1));
        path.setSegments(buildSegments(path.getPathId(), switches));
        path.setHaSubFlow(haSubFlow);
        return path;
    }

}
