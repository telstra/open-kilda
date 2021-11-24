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

package org.openkilda.rulemanager.factory.utils;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Utils.buildSwitch;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.MacAddress;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.collect.Sets;
import org.junit.Test;

public class UtilsTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow";
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int PORT_NUMBER_3 = 3;
    public static final int VLAN = 4;
    public static final int VNI = 5;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, Sets.newHashSet(RESET_COUNTS_FLAG, METERS));
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, Sets.newHashSet(RESET_COUNTS_FLAG, METERS));
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 123);
    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(COOKIE)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_2)
            .segments(newArrayList(PathSegment.builder()
                    .pathId(PATH_ID)
                    .srcPort(PORT_NUMBER_2)
                    .srcSwitch(SWITCH_1)
                    .destPort(PORT_NUMBER_3)
                    .destSwitch(SWITCH_2)
                    .build()))
            .build();
    public static final FlowPath ONE_SWITCH_PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(COOKIE)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_1)
            .build();

    @Test
    public void oneSwitchFlowOutPortTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, PORT_NUMBER_1, PORT_NUMBER_3);
        assertEquals(PORT_NUMBER_3, Utils.getOutPort(ONE_SWITCH_PATH, flow));
    }

    @Test
    public void multiSwitchFlowOutPortTest() {
        assertEquals(PORT_NUMBER_2, Utils.getOutPort(PATH, null));
    }

    @Test(expected = IllegalStateException.class)
    public void multiSwitchPathWithoutSegmentsOutPortTest() {
        FlowPath path = FlowPath.builder()
                .pathId(PATH_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .segments(newArrayList())
                .build();
        Utils.getOutPort(path, null);
    }

    @Test
    public void isFullPortFlowTest() {
        assertFalse(Utils.isFullPortEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, VLAN)));
        assertTrue(Utils.isFullPortEndpoint(new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, 0)));
    }

    @Test
    public void buildNoviflowPushVxlanTest() {
        PushVxlanAction pushVxlan = Utils.buildPushVxlan(
                VNI, SWITCH_ID_1, SWITCH_ID_2, VXLAN_UDP_SRC, Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN));
        assertPushVxlan(ActionType.PUSH_VXLAN_NOVIFLOW, pushVxlan);
    }

    @Test
    public void buildOvsPushVxlanTest() {
        PushVxlanAction pushVxlan = Utils.buildPushVxlan(
                VNI, SWITCH_ID_1, SWITCH_ID_2, VXLAN_UDP_SRC, Sets.newHashSet(KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        assertPushVxlan(ActionType.PUSH_VXLAN_OVS, pushVxlan);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildInvalidPushVxlanTest() {
        Utils.buildPushVxlan(VNI, SWITCH_ID_1, SWITCH_ID_2, VXLAN_UDP_SRC, Sets.newHashSet());
    }

    private void assertPushVxlan(ActionType actionType, PushVxlanAction pushVxlanAction) {
        assertEquals(actionType, pushVxlanAction.getType());
        assertEquals(new MacAddress(SWITCH_ID_1.toMacAddress()), pushVxlanAction.getSrcMacAddress());
        assertEquals(new MacAddress(SWITCH_ID_2.toMacAddress()), pushVxlanAction.getDstMacAddress());
        assertEquals(Constants.VXLAN_SRC_IPV4_ADDRESS, pushVxlanAction.getSrcIpv4Address());
        assertEquals(Constants.VXLAN_DST_IPV4_ADDRESS, pushVxlanAction.getDstIpv4Address());
        assertEquals(Constants.VXLAN_UDP_SRC, pushVxlanAction.getUdpSrc());
        assertEquals(VNI, pushVxlanAction.getVni());
    }

    private Flow buildFlow(FlowPath path, int srcPort, int dstPort) {
        Flow flow = Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(path.getSrcSwitch())
                .srcPort(srcPort)
                .destSwitch(path.getDestSwitch())
                .destPort(dstPort)
                .build();
        flow.setForwardPath(path);
        return flow;
    }
}
