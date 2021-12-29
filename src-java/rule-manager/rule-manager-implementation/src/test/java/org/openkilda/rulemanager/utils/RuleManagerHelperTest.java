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

package org.openkilda.rulemanager.utils;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.rulemanager.utils.RuleManagerHelper.checkCircularDependencies;
import static org.openkilda.rulemanager.utils.RuleManagerHelper.removeDuplicateCommands;
import static org.openkilda.rulemanager.utils.RuleManagerHelper.sortCommandsByDependencies;

import org.openkilda.model.GroupId;
import org.openkilda.model.IPv4Address;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RuleManagerHelperTest {

    public static final MeterId METER_ID_1 = new MeterId(1);
    public static final MeterId METER_ID_2 = new MeterId(2);
    public static final GroupId GROUP_ID_1 = new GroupId(3);
    public static final GroupId GROUP_ID_2 = new GroupId(4);
    public static final PortNumber PORT_NUMBER = new PortNumber(5);
    public static final short VLAN_ID = 6;
    public static final int VNI = 7;
    public static final MacAddress SRC_MAC_ADDRESS = new MacAddress("11:11:11:11:11:11");
    public static final MacAddress DST_MAC_ADDRESS = new MacAddress("22:22:22:22:22:22");
    public static final IPv4Address SRC_IPV4_ADDRESS = new IPv4Address("192.168.0.1");
    public static final IPv4Address DST_IPV4_ADDRESS = new IPv4Address("192.168.0.2");
    public static final int UDP_SRC = 8;
    public static final int NUMBER_OF_BITS = 9;
    public static final SwitchId SWITCH_ID = new SwitchId(10);
    public static final int PRIORITY = 11;
    public static final int RATE_1 = 12;
    public static final int RATE_2 = 13;
    public static final int BURST = 14;

    @Test
    public void removeDuplicateCommandsDifferentVlanMatchTest() {
        FlowSpeakerData command1 = buildVlanMatchCommand(1);
        FlowSpeakerData command2 = buildVlanMatchCommand(2);
        assertEquals(2, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void removeDuplicateCommandsEqualVlanMatchTest() {
        FlowSpeakerData command1 = buildVlanMatchCommand(1);
        FlowSpeakerData command2 = buildVlanMatchCommand(1);
        assertEquals(1, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void removeDuplicateCommandsFullEqualFlowCommandTest() {
        FlowSpeakerData command1 = buildFullFlowSpeakerCommandData();
        FlowSpeakerData command2 = buildFullFlowSpeakerCommandData();
        assertEquals(1, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void removeDuplicateCommandsDifferentFlowCommandTest() {
        FlowSpeakerData command1 = buildFullFlowSpeakerCommandData(METER_ID_1);
        FlowSpeakerData command2 = buildFullFlowSpeakerCommandData(METER_ID_2);
        assertEquals(2, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void removeDuplicateCommandsFullEqualMeterCommandTest() {
        MeterSpeakerData command1 = buildFullMeterSpeakerCommandData();
        MeterSpeakerData command2 = buildFullMeterSpeakerCommandData();
        assertEquals(1, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void removeDuplicateCommandsDifferentMeterCommandTest() {
        MeterSpeakerData command1 = buildFullMeterSpeakerCommandData(RATE_1);
        MeterSpeakerData command2 = buildFullMeterSpeakerCommandData(RATE_2);
        assertEquals(2, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void removeDuplicateCommandsFullEqualGroupCommandTest() {
        GroupSpeakerData command1 = buildFullGroupSpeakerCommandData();
        GroupSpeakerData command2 = buildFullGroupSpeakerCommandData();
        assertEquals(1, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void removeDuplicateCommandsDifferentGroupCommandTest() {
        GroupSpeakerData command1 = buildFullGroupSpeakerCommandData(GROUP_ID_1);
        GroupSpeakerData command2 = buildFullGroupSpeakerCommandData(GROUP_ID_2);
        assertEquals(2, removeDuplicateCommands(newArrayList(command1, command2)).size());
    }

    @Test
    public void checkCircularDependenciesTest() {
        checkCircularDependencies(new ArrayList<>());

        GroupSpeakerData groupCommand = buildFullGroupSpeakerCommandData(GROUP_ID_1);
        groupCommand.getDependsOn().clear();
        checkCircularDependencies(newArrayList(groupCommand));

        FlowSpeakerData command1 = buildFullFlowSpeakerCommandData(METER_ID_1, null);
        FlowSpeakerData command2 = buildFullFlowSpeakerCommandData(METER_ID_1, command1.getUuid());
        FlowSpeakerData command3 = buildFullFlowSpeakerCommandData(METER_ID_2, command2.getUuid());
        checkCircularDependencies(newArrayList(command1, command2, command3));
        // if there are no exceptions - test passed
    }

    @Test(expected = IllegalStateException.class)
    public void checkCircularDependenciesUnknownDependsOnTest() {
        FlowSpeakerData command = buildFullFlowSpeakerCommandData(METER_ID_1, UUID.randomUUID());
        checkCircularDependencies(newArrayList(command));
    }

    @Test(expected = IllegalStateException.class)
    public void checkCircularDependenciesLoopTest() {
        FlowSpeakerData command = buildFullFlowSpeakerCommandData(METER_ID_1, null);
        command.getDependsOn().add(command.getUuid());
        checkCircularDependencies(newArrayList(command));
    }

    @Test(expected = IllegalStateException.class)
    public void checkCircularDependenciesCycle3Test() {
        FlowSpeakerData command1 = buildFullFlowSpeakerCommandData(METER_ID_1, null);
        FlowSpeakerData command2 = buildFullFlowSpeakerCommandData(METER_ID_1, command1.getUuid());
        FlowSpeakerData command3 = buildFullFlowSpeakerCommandData(METER_ID_2, command2.getUuid());
        command1.getDependsOn().add(command3.getUuid());
        checkCircularDependencies(newArrayList(command1, command2, command3));
    }

    @Test(expected = IllegalStateException.class)
    public void checkCircularDependenciesCycle2Test() {
        FlowSpeakerData command1 = buildFullFlowSpeakerCommandData(METER_ID_1, null);
        FlowSpeakerData command2 = buildFullFlowSpeakerCommandData(METER_ID_1, command1.getUuid());
        command1.getDependsOn().add(command2.getUuid());
        checkCircularDependencies(newArrayList(command1, command2));
    }

    @Test
    public void sortCommandsByDependencies3Test() {
        FlowSpeakerData command1 = buildFullFlowSpeakerCommandData(METER_ID_1, null);
        FlowSpeakerData command2 = buildFullFlowSpeakerCommandData(METER_ID_1, command1.getUuid());
        FlowSpeakerData command3 = buildFullFlowSpeakerCommandData(METER_ID_2, command2.getUuid());
        List<SpeakerData> result = sortCommandsByDependencies(newArrayList(command3, command2, command1));

        assertEquals(3, result.size());
        assertEquals(command1.getUuid(), result.get(0).getUuid());
        assertEquals(command2.getUuid(), result.get(1).getUuid());
        assertEquals(command3.getUuid(), result.get(2).getUuid());
    }

    @Test
    public void sortCommandsByDependencies2Test() {
        FlowSpeakerData command1 = buildFullFlowSpeakerCommandData(METER_ID_1, null);
        FlowSpeakerData command2 = buildFullFlowSpeakerCommandData(METER_ID_1, command1.getUuid());
        FlowSpeakerData command3 = buildFullFlowSpeakerCommandData(METER_ID_1, null);
        List<SpeakerData> result = sortCommandsByDependencies(newArrayList(command3, command2, command1));

        assertEquals(3, result.size());
        int command1Pos = -1;
        int command2Pos = -1;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).getUuid().equals(command1.getUuid())) {
                command1Pos = i;
            }
            if (result.get(i).getUuid().equals(command2.getUuid())) {
                command2Pos = i;
            }
        }
        assertTrue(command1Pos < command2Pos);
    }


    private FlowSpeakerData buildVlanMatchCommand(int vlan) {
        return FlowSpeakerData.builder()
                .match(newHashSet(FieldMatch.builder().field(Field.VLAN_VID).value(vlan).build()))
                .build();
    }

    private FlowSpeakerData buildFullFlowSpeakerCommandData() {
        return buildFullFlowSpeakerCommandData(METER_ID_1, UUID.randomUUID());
    }

    private FlowSpeakerData buildFullFlowSpeakerCommandData(MeterId goToMeterId) {
        return buildFullFlowSpeakerCommandData(goToMeterId, UUID.randomUUID());
    }

    private FlowSpeakerData buildFullFlowSpeakerCommandData(MeterId goToMeterId, UUID dependsOnUuid) {
        Set<FieldMatch> match = Arrays.stream(Field.values())
                .map(f -> FieldMatch.builder().field(f).value(f.ordinal()).mask(f.ordinal() + 1L).build())
                .collect(Collectors.toSet());
        Set<OfFlowFlag> flags = Sets.newHashSet(OfFlowFlag.values());
        List<Action> applyActions = new ArrayList<>(buildAllActions());
        applyActions.addAll(buildAllActions());

        Instructions instructions = Instructions.builder()
                .goToMeter(goToMeterId)
                .goToTable(OfTable.INPUT)
                .writeMetadata(new OfMetadata(15, 0xff))
                .writeActions(new HashSet<>(buildAllActions()))
                .applyActions(applyActions)
                .build();

        return FlowSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .cookie(new Cookie(123))
                .priority(PRIORITY)
                .table(OfTable.INPUT)
                .match(match)
                .instructions(instructions)
                .flags(flags)
                .switchId(SWITCH_ID)
                .ofVersion(OfVersion.OF_13)
                .dependsOn(dependsOnUuid == null ? newArrayList() : newArrayList(dependsOnUuid))
                .build();
    }

    private MeterSpeakerData buildFullMeterSpeakerCommandData() {
        return buildFullMeterSpeakerCommandData(RATE_1);
    }

    private MeterSpeakerData buildFullMeterSpeakerCommandData(int rate) {
        return MeterSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .meterId(METER_ID_1)
                .rate(rate)
                .burst(BURST)
                .flags(Sets.newHashSet(MeterFlag.values()))
                .switchId(SWITCH_ID)
                .ofVersion(OfVersion.OF_13)
                .dependsOn(newArrayList(UUID.randomUUID()))
                .build();
    }

    private GroupSpeakerData buildFullGroupSpeakerCommandData() {
        return buildFullGroupSpeakerCommandData(GROUP_ID_1);
    }

    private GroupSpeakerData buildFullGroupSpeakerCommandData(GroupId groupId) {
        Bucket bucket = Bucket.builder()
                .watchGroup(WatchGroup.ANY)
                .watchPort(WatchPort.ANY)
                .writeActions(new HashSet<>(buildAllActions()))
                .build();

        return GroupSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .groupId(groupId)
                .type(GroupType.ALL)
                .buckets(newArrayList(bucket))
                .switchId(SWITCH_ID)
                .ofVersion(OfVersion.OF_13)
                .dependsOn(newArrayList(UUID.randomUUID()))
                .build();
    }

    private List<Action> buildAllActions() {
        List<Action> actions = new ArrayList<>();
        actions.add(new GroupAction(GROUP_ID_1));
        actions.add(new MeterAction(METER_ID_1));
        actions.add(new PopVlanAction());
        actions.add(new PopVxlanAction(ActionType.POP_VXLAN_OVS));
        actions.add(new PortOutAction(PORT_NUMBER));
        actions.add(PushVlanAction.builder().vlanId(VLAN_ID).build());
        actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ID).build());
        actions.add(CopyFieldAction.builder()
                .srcOffset(0)
                .dstOffset(0)
                .numberOfBits(NUMBER_OF_BITS)
                .oxmSrcHeader(OpenFlowOxms.NOVIFLOW_TX_TIMESTAMP)
                .oxmDstHeader(OpenFlowOxms.NOVIFLOW_PACKET_OFFSET)
                .build());
        actions.add(PushVxlanAction.builder()
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(VNI)
                .srcMacAddress(SRC_MAC_ADDRESS)
                .dstMacAddress(DST_MAC_ADDRESS)
                .srcIpv4Address(SRC_IPV4_ADDRESS)
                .dstIpv4Address(DST_IPV4_ADDRESS)
                .udpSrc(UDP_SRC)
                .build());
        return actions;
    }
}
