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

package org.openkilda.rulemanager.factory.generator.flow.haflow;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.buildSwitchProperties;
import static org.openkilda.rulemanager.Utils.buildSwitchPropertiesServer42;

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
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSubType;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.InMemoryDataAdapter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class HaFlowRulesBaseTest {

    public static final String HA_FLOW_ID = "ha_flow_id";
    public static final String SUB_FLOW_1 = "sub_flow_1";
    public static final String SUB_FLOW_2 = "sub_flow_2";
    public static final PathId PATH_ID_1 = new PathId("path_id_1");
    public static final PathId PATH_ID_2 = new PathId("path_id_2");
    public static final PathId PATH_ID_3 = new PathId("path_id_3");
    public static final PathId PATH_ID_4 = new PathId("path_id_4");
    public static final PathId PATH_ID_5 = new PathId("path_id_5");
    public static final PathId PATH_ID_6 = new PathId("path_id_6");
    public static final int PORT_NUMBER_1 = 1;
    public static final MeterId SHARED_POINT_METER_ID = new MeterId(1);
    public static final MeterId Y_POINT_METER_ID = new MeterId(2);
    public static final MeterId SUB_FLOW_1_METER_ID = new MeterId(3);
    public static final MeterId SUB_FLOW_2_METER_ID = new MeterId(4);
    public static final GroupId GROUP_ID = new GroupId(5);
    public static final Set<SwitchFeature> FEATURES = Sets.newHashSet(
            RESET_COUNTS_FLAG, METERS, NOVIFLOW_PUSH_POP_VXLAN);
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    public static final SwitchId SWITCH_ID_5 = new SwitchId(5);
    public static final SwitchId SWITCH_ID_6 = new SwitchId(6);
    public static final SwitchId SWITCH_ID_7 = new SwitchId(7);
    public static final SwitchId SWITCH_ID_8_SERVER42 = new SwitchId(8);
    public static final SwitchId SWITCH_ID_9_SERVER42 = new SwitchId(9);
    public static final SwitchId SWITCH_ID_10_SERVER42 = new SwitchId(10);

    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, FEATURES);
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, FEATURES);
    public static final Switch SWITCH_3 = buildSwitch(SWITCH_ID_3, FEATURES);
    public static final Switch SWITCH_4 = buildSwitch(SWITCH_ID_4, FEATURES);
    public static final Switch SWITCH_5 = buildSwitch(SWITCH_ID_5, FEATURES);
    public static final Switch SWITCH_6 = buildSwitch(SWITCH_ID_6, FEATURES);
    public static final Switch SWITCH_7 = buildSwitch(SWITCH_ID_7, FEATURES);
    public static final Switch SWITCH_8_SERVER42 = buildSwitch(SWITCH_ID_8_SERVER42, FEATURES);
    public static final Switch SWITCH_9_SERVER42 = buildSwitch(SWITCH_ID_9_SERVER42, FEATURES);
    public static final Switch SWITCH_10_SERVER42 = buildSwitch(SWITCH_ID_10_SERVER42, FEATURES);

    public static final Map<SwitchId, SwitchProperties> SWITCH_PROPERTIES_MAP = ImmutableMap.of(
            SWITCH_ID_1, buildSwitchProperties(SWITCH_1),
            SWITCH_ID_2, buildSwitchProperties(SWITCH_2),
            SWITCH_ID_3, buildSwitchProperties(SWITCH_3),
            SWITCH_ID_4, buildSwitchProperties(SWITCH_4),
            SWITCH_ID_5, buildSwitchProperties(SWITCH_5),
            SWITCH_ID_7, buildSwitchProperties(SWITCH_7),
            SWITCH_ID_8_SERVER42, buildSwitchPropertiesServer42(SWITCH_8_SERVER42),
            SWITCH_ID_9_SERVER42, buildSwitchPropertiesServer42(SWITCH_9_SERVER42),
            SWITCH_ID_10_SERVER42, buildSwitchPropertiesServer42(SWITCH_10_SERVER42));

    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            14, FlowEncapsulationType.TRANSIT_VLAN);

    public static final FlowSegmentCookie FORWARD_COOKIE = FlowSegmentCookie.builder()
            .direction(FlowPathDirection.FORWARD).flowEffectiveId(1).subType(FlowSubType.SHARED).build();
    public static final FlowSegmentCookie REVERSE_COOKIE = FlowSegmentCookie.builder()
            .direction(FlowPathDirection.REVERSE).flowEffectiveId(1).subType(FlowSubType.SHARED).build();

    public static final FlowSegmentCookie FORWARD_SUB_COOKIE_1 = FORWARD_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_1).build();
    public static final FlowSegmentCookie REVERSE_SUB_COOKIE_1 = REVERSE_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_1).build();
    public static final FlowSegmentCookie FORWARD_SUB_COOKIE_2 = FORWARD_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_2).build();
    public static final FlowSegmentCookie REVERSE_SUB_COOKIE_2 = REVERSE_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_2).build();

    public static final int SHARED_INNER_VLAN = 100;



    protected HaFlow buildYShapedYEqualsSharedHaFlowServer42() {
        // HA-flow       9
        //              /
        //   shared_sw 8
        //              \
        //               10

        HaFlow haFlow = buildHaFlow(SWITCH_8_SERVER42, SWITCH_9_SERVER42, SWITCH_10_SERVER42, SHARED_INNER_VLAN);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_8_SERVER42, SWITCH_9_SERVER42);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_8_SERVER42, SWITCH_10_SERVER42);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_8_SERVER42);
        return haFlow;
    }

    protected HaFlow buildYShapedYEqualsSharedHaFlow() {
        // HA-flow       2
        //              /
        //   shared_sw 8
        //              \
        //               3

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_2, SWITCH_3);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_3);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_1);
        return haFlow;
    }

    protected HaFlow buildLongYShapedHaFlow() {
        // HA-flow             4-----5
        //                    /
        //      1------2-----3
        //                    \
        //                     6-----7

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_5, SWITCH_7);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3, SWITCH_4, SWITCH_5);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3, SWITCH_6, SWITCH_7);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_3);
        return haFlow;
    }

    protected HaFlow buildIShapedDifferentLengthHaFlow() {
        // HA-flow             4
        //                    /
        //      1------2-----3
        //                   ^
        //                   Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_3, SWITCH_4);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3, SWITCH_4);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_3);
        return haFlow;
    }

    protected HaFlow buildIShapedEqualLengthHaFlow() {
        // HA-flow
        //
        //      1------2-----3
        //                   ^
        //                   Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_3, SWITCH_3);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_3);
        return haFlow;
    }

    protected HaFlow buildIShapedEqualLengthDifferentIslsHaFlow() {
        // HA-flow
        //
        //      1======2
        //      ^
        //      Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_2, SWITCH_2);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_1);
        return haFlow;
    }

    protected HaFlow buildIShapedOneSwitchHaFlow() {
        // HA-flow
        //
        //      1------2-----3
        //      ^
        //  Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_1, SWITCH_3);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_1);
        return haFlow;
    }

    protected HaFlow buildIShapedOneSwitchHaFlowServer42() {
        // HA-flow
        //
        //      8------9
        //      ^
        //  Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_8_SERVER42, SWITCH_8_SERVER42, SWITCH_9_SERVER42, SHARED_INNER_VLAN);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_8_SERVER42);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_8_SERVER42, SWITCH_9_SERVER42);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_8_SERVER42);
        return haFlow;
    }

    protected HaFlow buildYShapedHaFlow() {
        // HA-flow       3
        //              /
        //      1------2
        //              \
        //               4

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_3, SWITCH_4);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_4);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_2);
        return haFlow;
    }

    protected static void setYPoint(HaFlow haFlow, SwitchId switchId3) {
        haFlow.getForwardPath().setYPointSwitchId(switchId3);
        haFlow.getReversePath().setYPointSwitchId(switchId3);
    }

    protected static void setMainPaths(HaFlow haFlow, PathId forwardId, PathId reverseId,
                                       FlowPath[] firstSubPaths, FlowPath[] secondSubPaths) {
        firstSubPaths[1].setMeterId(SUB_FLOW_1_METER_ID);
        secondSubPaths[1].setMeterId(SUB_FLOW_2_METER_ID);

        HaFlowPath forwardHaPath = HaFlowPath.builder()
                .cookie(FORWARD_COOKIE)
                .sharedPointMeterId(SHARED_POINT_METER_ID)
                .yPointMeterId(null)
                .yPointGroupId(GROUP_ID)
                .haPathId(forwardId)
                .sharedSwitch(haFlow.getSharedSwitch())
                .build();
        forwardHaPath.setHaSubFlows(haFlow.getHaSubFlows());
        forwardHaPath.setSubPaths(Lists.newArrayList(firstSubPaths[0], secondSubPaths[0]));
        haFlow.setForwardPath(forwardHaPath);

        HaFlowPath reverseHaPath = HaFlowPath.builder()
                .cookie(REVERSE_COOKIE)
                .sharedPointMeterId(null)
                .yPointMeterId(Y_POINT_METER_ID)
                .yPointGroupId(null)
                .haPathId(reverseId)
                .sharedSwitch(haFlow.getSharedSwitch())
                .build();
        reverseHaPath.setHaSubFlows(haFlow.getHaSubFlows());
        reverseHaPath.setSubPaths(Lists.newArrayList(firstSubPaths[1], secondSubPaths[1]));
        haFlow.setReversePath(reverseHaPath);
    }

    protected static FlowPath[] buildSubPathPair(
            PathId forwardId, PathId reverseId, FlowSegmentCookie forwardCookie, FlowSegmentCookie reverseCookie,
            HaSubFlow haSubFlow, Switch... switches) {
        Switch[] reverseSwitches = Arrays.copyOf(switches, switches.length);
        ArrayUtils.reverse(reverseSwitches);
        return new FlowPath[]{
                buildSubPath(forwardId, haSubFlow, forwardCookie, switches),
                buildSubPath(reverseId, haSubFlow, reverseCookie, reverseSwitches)
        };
    }

    protected static HaFlow buildHaFlow(Switch sharedSwitch, Switch endpointSwitch1, Switch endpointSwitch2) {
        return buildHaFlow(sharedSwitch, endpointSwitch1, endpointSwitch2, 0);
    }

    private static HaFlow buildHaFlow(Switch sharedSwitch, Switch endpointSwitch1, Switch endpointSwitch2,
                                      int sharedInnerVlan) {
        HaFlow haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedInnerVlan(sharedInnerVlan)
                .sharedPort(PORT_NUMBER_1)
                .sharedSwitch(sharedSwitch)
                .build();
        haFlow.setHaSubFlows(Lists.newArrayList(
                buildHaSubFlow(endpointSwitch1, SUB_FLOW_1), buildHaSubFlow(endpointSwitch2, SUB_FLOW_2)));
        return haFlow;
    }

    private static HaSubFlow buildHaSubFlow(Switch sw, String subFlowId) {
        return HaSubFlow.builder()
                .haSubFlowId(subFlowId)
                .endpointSwitch(sw)
                .build();
    }

    private static FlowPath buildSubPath(PathId pathId, HaSubFlow haSubFlow, FlowSegmentCookie cookie,
                                         Switch... switches) {
        FlowPath subPath = FlowPath.builder()
                .cookie(cookie)
                .pathId(pathId)
                .srcSwitch(switches[0])
                .destSwitch(switches[switches.length - 1])
                .build();
        List<PathSegment> segments = new ArrayList<>();
        for (int i = 1; i < switches.length; i++) {
            segments.add(PathSegment.builder()
                    .pathId(pathId)
                    .srcSwitch(switches[i - 1])
                    .destSwitch(switches[i])
                    .build());
        }
        subPath.setSegments(segments);
        subPath.setHaSubFlow(haSubFlow);
        return subPath;
    }

    protected DataAdapter buildAdapter(HaFlow haFlow) {
        List<FlowPath> subPaths = haFlow.getPaths().stream().flatMap(path -> path.getSubPaths().stream())
                .collect(Collectors.toList());


        Set<Switch> switches = Sets.newHashSet(haFlow.getSharedSwitch());
        haFlow.getHaSubFlows().stream().map(HaSubFlow::getEndpointSwitch).forEach(switches::add);
        subPaths.stream()
                .flatMap(path -> path.getSegments().stream())
                .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch()))
                .forEach(switches::add);

        Map<PathId, HaFlow> haFlowMap = haFlow.getPaths().stream()
                .collect(Collectors.toMap(HaFlowPath::getHaPathId, HaFlowPath::getHaFlow));
        for (FlowPath subPath : subPaths) {
            haFlowMap.put(subPath.getPathId(), subPath.getHaFlowPath().getHaFlow());
        }

        Map<PathId, FlowTransitEncapsulation> encapsulationMap = new HashMap<>();
        for (HaFlowPath haFlowPath : haFlow.getPaths()) {
            encapsulationMap.put(haFlowPath.getHaPathId(), VLAN_ENCAPSULATION);
        }

        return InMemoryDataAdapter.builder()
                .switchProperties(SWITCH_PROPERTIES_MAP)
                .commonFlowPaths(new HashMap<>())
                .haFlowSubPaths(subPaths.stream().collect(toMap(FlowPath::getPathId, identity())))
                .transitEncapsulations(encapsulationMap)
                .switches(switches.stream().collect(Collectors.toMap(Switch::getSwitchId, identity())))
                .haFlowMap(haFlowMap)
                .haFlowPathMap(haFlow.getPaths().stream().collect(toMap(HaFlowPath::getHaPathId, identity())))
                .build();
    }


    protected void assertFlowTables(Collection<SpeakerData> commands, OfTable... expectedTables) {
        List<OfTable> actualTables = commands.stream()
                .filter(FlowSpeakerData.class::isInstance)
                .map(FlowSpeakerData.class::cast)
                .map(FlowSpeakerData::getTable)
                .sorted()
                .collect(Collectors.toList());

        Arrays.sort(expectedTables);
        Assertions.assertEquals(Arrays.asList(expectedTables), actualTables);
    }

    protected int getFlowCount(Collection<SpeakerData> commands) {
        return getCommandCount(commands, FlowSpeakerData.class);
    }

    protected int getMeterCount(Collection<SpeakerData> commands) {
        return getCommandCount(commands, MeterSpeakerData.class);
    }

    protected int getGroupCount(Collection<SpeakerData> commands) {
        return getCommandCount(commands, GroupSpeakerData.class);
    }

    private int getCommandCount(Collection<SpeakerData> commands, Class<? extends SpeakerData> clazz) {
        return (int) commands.stream().filter(clazz::isInstance).count();
    }

    protected Map<SwitchId, List<SpeakerData>> groupBySwitchId(Collection<SpeakerData> commands) {
        return commands.stream().collect(Collectors.groupingBy(SpeakerData::getSwitchId));
    }
}
