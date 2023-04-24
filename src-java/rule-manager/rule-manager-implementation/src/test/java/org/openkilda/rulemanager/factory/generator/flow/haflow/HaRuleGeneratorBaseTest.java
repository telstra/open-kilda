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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.buildSwitch;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSubType;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HaRuleGeneratorBaseTest {
    protected static final long MIN_BIRST_SIZE = 1024L;
    protected static final PathId PATH_ID_1 = new PathId("path_id_1");
    protected static final PathId PATH_ID_2 = new PathId("path_id_2");
    protected static final PathId PATH_ID_3 = new PathId("path_id_3");
    protected static final String HA_FLOW_ID = "ha_flow";
    protected static final String HA_SUB_FLOW_ID = "sub_flow";
    protected static final int PORT_NUMBER_1 = 1;
    protected static final int PORT_NUMBER_2 = 2;
    protected static final int PORT_NUMBER_3 = 3;
    protected static final int PORT_NUMBER_4 = 4;
    protected static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    protected static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    protected static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final HashSet<SwitchFeature> FEATURES = Sets.newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN, METERS);
    protected static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, Sets.newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN));
    protected static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, FEATURES);
    protected static final Switch SWITCH_3 = buildSwitch(SWITCH_ID_3, FEATURES);
    protected static final int OUTER_VLAN_ID_1 = 10;
    protected static final int OUTER_VLAN_ID_2 = 11;
    protected static final int INNER_VLAN_ID_1 = 12;
    protected static final int INNER_VLAN_ID_2 = 13;
    protected static final int TRANSIT_VLAN_ID = 14;
    protected static final int VXLAN_VNI = 15;
    protected static final MeterId METER_ID = new MeterId(16);
    protected static final GroupId GROUP_ID = new GroupId(17);
    protected static final UUID METER_COMMAND_UUID = UUID.randomUUID();
    protected static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID, FlowEncapsulationType.TRANSIT_VLAN);
    protected static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI, FlowEncapsulationType.VXLAN);
    protected static final FlowSegmentCookie FORWARD_COOKIE = FlowSegmentCookie.builder()
            .type(CookieType.SERVICE_OR_FLOW_SEGMENT)
            .direction(FlowPathDirection.FORWARD)
            .flowEffectiveId(123)
            .subType(FlowSubType.HA_SUB_FLOW_1)
            .build();
    protected static final FlowSegmentCookie FORWARD_COOKIE_2 = FlowSegmentCookie.builder()
            .type(CookieType.SERVICE_OR_FLOW_SEGMENT)
            .direction(FlowPathDirection.FORWARD)
            .flowEffectiveId(456)
            .subType(FlowSubType.HA_SUB_FLOW_2)
            .build();
    protected static final FlowSegmentCookie REVERSE_COOKIE = FORWARD_COOKIE.toBuilder()
            .direction(FlowPathDirection.REVERSE)
            .build();
    protected static final FlowSegmentCookie SHARED_FORWARD_COOKIE = FORWARD_COOKIE.toBuilder()
            .subType(FlowSubType.SHARED)
            .build();
    protected static final HaFlow HA_FLOW = HaFlow.builder()
            .haFlowId(HA_FLOW_ID)
            .sharedSwitch(SWITCH_1)
            .sharedPort(PORT_NUMBER_1)
            .sharedOuterVlan(OUTER_VLAN_ID_2)
            .sharedInnerVlan(INNER_VLAN_ID_2)
            .build();

    protected static final double BURST_COEFFICIENT = 1.05;
    public static final long BANDWIDTH = 1234;

    RuleManagerConfig config;

    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getFlowMeterBurstCoefficient()).thenReturn(BURST_COEFFICIENT);
        when(config.getFlowMeterMinBurstSizeInKbits()).thenReturn(MIN_BIRST_SIZE);
    }

    protected static Set<FieldMatch> buildExpectedTransitVlanMatch(int port, int vlanId) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(vlanId).build());
    }

    protected static Set<FieldMatch> buildExpectedVxlanMatch(int port, int vni) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(Constants.VXLAN_UDP_DST).build(),
                FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(vni).build());
    }

    protected static HaSubFlow buildHaSubFlow(int outerVlan, int innerVlan) {
        return HaSubFlow.builder()
                .haSubFlowId(HA_SUB_FLOW_ID)
                .endpointSwitch(SWITCH_2)
                .endpointPort(PORT_NUMBER_4)
                .endpointVlan(outerVlan)
                .endpointInnerVlan(innerVlan)
                .build();
    }

    protected static FlowPath buildSubPath(int outerVlan, int innerVlan) {
        return buildSubPath(SWITCH_1, SWITCH_2, FORWARD_COOKIE, outerVlan, innerVlan);
    }

    protected static FlowPath buildSubPath(
            Switch srcSwitch, Switch dstSwitch, FlowSegmentCookie cookie, int outerVlan, int innerVlan) {
        return buildSubPath(PATH_ID_1, srcSwitch, dstSwitch, cookie, outerVlan, innerVlan);
    }

    protected static FlowPath buildSubPath(
            PathId pathId, Switch srcSwitch, Switch dstSwitch, FlowSegmentCookie cookie, int outerVlan,
            int innerVlan) {
        return buildSubPath(pathId, srcSwitch, dstSwitch, PORT_NUMBER_2, cookie, outerVlan, innerVlan);
    }

    protected static FlowPath buildSubPath(
            PathId pathId, Switch srcSwitch, Switch dstSwitch, int srcPort, FlowSegmentCookie cookie, int outerVlan,
            int innerVlan) {
        List<PathSegment> segments = new ArrayList<>();
        if (!srcSwitch.getSwitchId().equals(dstSwitch.getSwitchId())) {
            segments.add(PathSegment.builder()
                    .pathId(pathId)
                    .srcPort(srcPort)
                    .srcSwitch(SWITCH_1)
                    .destPort(PORT_NUMBER_3)
                    .destSwitch(SWITCH_2)
                    .build());
        }
        FlowPath path = FlowPath.builder()
                .pathId(pathId)
                .cookie(cookie)
                .srcSwitch(srcSwitch)
                .srcWithMultiTable(true)
                .destSwitch(dstSwitch)
                .destWithMultiTable(true)
                .bandwidth(BANDWIDTH)
                .segments(segments)
                .build();
        path.setHaSubFlow(buildHaSubFlow(outerVlan, innerVlan));
        return path;
    }
}
