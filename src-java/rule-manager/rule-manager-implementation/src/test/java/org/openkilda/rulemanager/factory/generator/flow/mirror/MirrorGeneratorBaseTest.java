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

package org.openkilda.rulemanager.factory.generator.flow.mirror;

import static com.google.common.collect.Sets.newHashSet;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowPathDirection.FORWARD;
import static org.openkilda.model.FlowPathDirection.REVERSE;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.buildSwitch;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;

import java.util.Set;

public class MirrorGeneratorBaseTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow";
    public static final MeterId METER_ID = new MeterId(17);
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int PORT_NUMBER_3 = 3;
    public static final int PORT_NUMBER_4 = 4;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final GroupId GROUP_ID = new GroupId(555);
    public static final Set<SwitchFeature> FEATURES = newHashSet(
            RESET_COUNTS_FLAG, METERS, NOVIFLOW_PUSH_POP_VXLAN);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, FEATURES);
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, FEATURES);
    public static final Switch SWITCH_3 = buildSwitch(SWITCH_ID_3, FEATURES);
    public static final int OUTER_VLAN_ID_1 = 10;
    public static final int OUTER_VLAN_ID_2 = 11;
    public static final int INNER_VLAN_ID_1 = 12;
    public static final int INNER_VLAN_ID_2 = 13;
    public static final int TRANSIT_VLAN_ID_1 = 14;
    public static final int TRANSIT_VLAN_ID_2 = 15;
    public static final int VXLAN_VNI_1 = 16;
    public static final int VXLAN_VNI_2 = 17;
    public static final int BANDWIDTH = 1000;
    public static final int MIRROR_PORT_1 = 18;
    public static final short MIRROR_OUTER_VLAN_1 = 20;
    public static final short MIRROR_INNER_VLAN_1 = 22;
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(FORWARD, 123);
    public static final int UNMASKED_COOKIE_1 = 9;
    public static final FlowSegmentCookie FORWARD_MIRROR_COOKIE_1 = new FlowSegmentCookie(FORWARD, UNMASKED_COOKIE_1);
    public static final FlowSegmentCookie REVERSE_MIRROR_COOKIE_1 = new FlowSegmentCookie(REVERSE, UNMASKED_COOKIE_1);
    public static final String FLOW_MIRROR_ID_1 = "mirror_1";
    public static final PathId FORWARD_FLOW_MIRROR_PATH_ID_1 = new PathId("forward_1");
    public static final PathId REVERSE_FLOW_MIRROR_PATH_ID_1 = new PathId("reverse_1");
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID_1, TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI_1, FlowEncapsulationType.VXLAN);

    public static final double BURST_COEFFICIENT = 1.05;
    public static final FlowSegmentCookie MIRROR_COOKIE = COOKIE.toBuilder().mirror(true).build();

    protected static Bucket.BucketBuilder baseBucket() {
        return Bucket.builder().watchPort(WatchPort.ANY).watchGroup(WatchGroup.ANY);
    }

    protected static FlowMirrorPoints buildMirrorPoints(
            Switch mirrorSwitch, Switch sinkSwitch, MirrorDirection direction) {
        FlowMirrorPoints mirrorPoints = FlowMirrorPoints.builder()
                .mirrorSwitch(mirrorSwitch)
                .mirrorGroup(MirrorGroup.builder()
                        .flowId(FLOW_ID)
                        .pathId(PATH_ID)
                        .groupId(GROUP_ID)
                        .switchId(mirrorSwitch.getSwitchId())
                        .mirrorDirection(direction)
                        .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                        .build())
                .build();
        mirrorPoints.addFlowMirrors(buildSingleSwitchMirror(mirrorSwitch));
        return mirrorPoints;
    }

    protected static FlowMirror buildSingleSwitchMirror(Switch mirrorSwitch) {
        FlowMirror mirror = FlowMirror.builder()
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(mirrorSwitch)
                .flowMirrorId(FLOW_MIRROR_ID_1)
                .egressPort(MIRROR_PORT_1)
                .egressOuterVlan(MIRROR_OUTER_VLAN_1)
                .egressInnerVlan(MIRROR_INNER_VLAN_1)
                .build();

        FlowMirrorPath forward = FlowMirrorPath.builder()
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(mirrorSwitch)
                .mirrorPathId(FORWARD_FLOW_MIRROR_PATH_ID_1)
                .cookie(FORWARD_MIRROR_COOKIE_1)
                .segments(null)
                .dummy(false)
                .build();

        FlowMirrorPath reverse = FlowMirrorPath.builder()
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(mirrorSwitch)
                .mirrorPathId(REVERSE_FLOW_MIRROR_PATH_ID_1)
                .cookie(REVERSE_MIRROR_COOKIE_1)
                .segments(null)
                .dummy(true)
                .build();
        mirror.addMirrorPaths(forward, reverse);
        return mirror;
    }

    protected static PushVxlanAction buildPushVxlan(SwitchId srcSwitchId, SwitchId dstSwitchId, int vni) {
        return PushVxlanAction.builder()
                .srcMacAddress(new MacAddress(srcSwitchId.toMacAddress()))
                .dstMacAddress(new MacAddress(dstSwitchId.toMacAddress()))
                .srcIpv4Address(Constants.VXLAN_SRC_IPV4_ADDRESS)
                .dstIpv4Address(Constants.VXLAN_DST_IPV4_ADDRESS)
                .udpSrc(Constants.VXLAN_UDP_SRC)
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(vni).build();
    }
}
