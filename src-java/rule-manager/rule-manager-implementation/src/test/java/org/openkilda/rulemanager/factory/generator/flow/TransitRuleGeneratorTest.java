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

package org.openkilda.rulemanager.factory.generator.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class TransitRuleGeneratorTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, Sets.newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN));
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, Sets.newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN));
    public static final int VLAN = 5;
    public static final int VXLAN = 10;
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VLAN, FlowEncapsulationType.TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN, FlowEncapsulationType.VXLAN);
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(123);
    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(COOKIE)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_2)
            .build();

    @Test
    public void buildCorrectVlanMultiTableTransitRuleTest() {
        TransitRuleGenerator generator = TransitRuleGenerator.builder()
                .flowPath(PATH)
                .inPort(PORT_NUMBER_1)
                .outPort(PORT_NUMBER_2)
                .multiTable(true)
                .encapsulation(VLAN_ENCAPSULATION)
                .build();

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertTransitCommands(commands, OfTable.TRANSIT, VLAN_ENCAPSULATION);
    }

    @Test
    public void buildCorrectVlanSingleTransitRuleTest() {
        TransitRuleGenerator generator = TransitRuleGenerator.builder()
                .flowPath(PATH)
                .inPort(PORT_NUMBER_1)
                .outPort(PORT_NUMBER_2)
                .multiTable(false)
                .encapsulation(VLAN_ENCAPSULATION)
                .build();

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertTransitCommands(commands, OfTable.INPUT, VLAN_ENCAPSULATION);
    }

    @Test
    public void buildCorrectVxlanMultiTableTransitRuleTest() {
        TransitRuleGenerator generator = TransitRuleGenerator.builder()
                .flowPath(PATH)
                .inPort(PORT_NUMBER_1)
                .outPort(PORT_NUMBER_2)
                .multiTable(true)
                .encapsulation(VXLAN_ENCAPSULATION)
                .build();

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertTransitCommands(commands, OfTable.TRANSIT, VXLAN_ENCAPSULATION);
    }

    @Test
    public void buildCorrectVxlanSingleTableTransitRuleTest() {
        TransitRuleGenerator generator = TransitRuleGenerator.builder()
                .flowPath(PATH)
                .inPort(PORT_NUMBER_1)
                .outPort(PORT_NUMBER_2)
                .multiTable(false)
                .encapsulation(VXLAN_ENCAPSULATION)
                .build();

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertTransitCommands(commands, OfTable.INPUT, VXLAN_ENCAPSULATION);
    }

    private void assertTransitCommands(List<SpeakerCommandData> commands, OfTable table,
                                       FlowTransitEncapsulation encapsulation) {
        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(SWITCH_1.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(COOKIE, flowCommandData.getCookie());
        assertEquals(table, flowCommandData.getTable());
        assertEquals(Priority.FLOW_PRIORITY, flowCommandData.getPriority());


        Set<FieldMatch> expectedMatch;
        if (encapsulation.getType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
            expectedMatch = buildExpectedVlanMatch(PORT_NUMBER_1, encapsulation.getId());
        } else {
            expectedMatch = buildExpectedVxlanMatch(PORT_NUMBER_1, encapsulation.getId());
        }
        assertEqualsMatch(expectedMatch, flowCommandData.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(Lists.newArrayList(new PortOutAction(new PortNumber(PORT_NUMBER_2))))
                .build();
        assertEquals(expectedInstructions, flowCommandData.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), flowCommandData.getFlags());
    }

    private Set<FieldMatch> buildExpectedVlanMatch(int port, int vlanId) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(vlanId).build());
    }

    private Set<FieldMatch> buildExpectedVxlanMatch(int port, int vni) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(Constants.VXLAN_UDP_DST).build(),
                FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(vni).build());
    }

    @Test
    public void buildOneSwitchFlowTransitRuleTest() {
        FlowPath path = FlowPath.builder()
                .pathId(PATH_ID)
                .cookie(COOKIE)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_1)
                .build();

        TransitRuleGenerator generator = TransitRuleGenerator.builder()
                .flowPath(path)
                .inPort(PORT_NUMBER_1)
                .outPort(PORT_NUMBER_2)
                .multiTable(true)
                .encapsulation(VLAN_ENCAPSULATION)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }
}
