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

package org.openkilda.rulemanager.factory.generator.service.server42;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_SWAP_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE;
import static org.openkilda.rulemanager.Constants.MAC_ADDRESS_SIZE_IN_BITS;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_FLOW_RTT_VXLAN_TURNING_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.SwapFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class Server42FlowRttVxlanTurningRuleGeneratorTest {
    private Server42FlowRttVxlanTurningRuleGenerator generator;

    @Before
    public void setup() {
        generator = new Server42FlowRttVxlanTurningRuleGenerator();
    }

    @Test
    public void server42FlowRttVxlanTurningRuleGeneratorNoviflowTest() {
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(NOVIFLOW_SWAP_ETH_SRC_ETH_DST));
        testOutputRule(sw, ActionType.NOVI_SWAP_FIELD);
    }

    @Test
    public void server42FlowRttVxlanTurningRuleGeneratorOvsTest() {
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(KILDA_OVS_SWAP_FIELD));
        testOutputRule(sw, ActionType.KILDA_SWAP_FIELD);
    }

    private void testOutputRule(Switch sw, ActionType expectedSwapFieldType) {
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new Cookie(SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(SERVER_42_FLOW_RTT_VXLAN_TURNING_PRIORITY, flowCommandData.getPriority());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(sw.getSwitchId().toMacAddressAsLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(VXLAN_UDP_DST).build());
        assertEquals(expectedMatch, flowCommandData.getMatch());

        List<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT).build(),
                SwapFieldAction.builder()
                        .type(expectedSwapFieldType)
                        .numberOfBits(MAC_ADDRESS_SIZE_IN_BITS)
                        .srcOffset(0)
                        .dstOffset(0)
                        .oxmSrcHeader(OpenFlowOxms.ETH_SRC)
                        .oxmDstHeader(OpenFlowOxms.ETH_DST)
                        .build(),
                new PortOutAction(new PortNumber(SpecialPortType.IN_PORT)));

        Instructions expectedInstructions = Instructions.builder().applyActions(expectedApplyActions).build();
        assertEquals(expectedInstructions, flowCommandData.getInstructions());
    }
}
