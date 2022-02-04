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

package org.openkilda.floodlight.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.floodlight.converter.rulemanager.OfInstructionsConverter;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.SwapFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;

public class OfInstructionsConverterTest {

    @Test
    public void convertToRuleManagerInstructionsTest() {
        OFFactory factory = new OFFactoryVer13();
        List<OFInstruction> instructions = new ArrayList<>();
        instructions.add(factory.instructions().gotoTable(TableId.of(1)));
        instructions.add(factory.instructions().meter(2));
        instructions.add(factory.instructions().writeMetadata(U64.of(123), U64.of(234)));

        List<OFAction> actions = buildActions(factory);
        instructions.add(factory.instructions().applyActions(actions));

        Instructions actual = OfInstructionsConverter.INSTANCE.convertToRuleManagerInstructions(instructions);

        assertEquals(OfTable.PRE_INGRESS, actual.getGoToTable());
        assertEquals(new MeterId(2), actual.getGoToMeter());
        assertEquals(new OfMetadata(123, 234), actual.getWriteMetadata());
        assertEquals(12, actual.getApplyActions().size());

        List<Action> expectedActions = buildActions();
        assertTrue(actual.getApplyActions().containsAll(expectedActions));
    }

    @Test
    public void convertInstructionsTest() {
        OFFactory factory = new OFFactoryVer13();
        Instructions instructions = Instructions.builder()
                .goToTable(OfTable.PRE_INGRESS)
                .goToMeter(new MeterId(2))
                .writeMetadata(new OfMetadata(123, 234))
                .applyActions(buildActions())
                .build();

        List<OFInstruction> actual = OfInstructionsConverter.INSTANCE.convertInstructions(instructions, factory);
        assertEquals(4, actual.size());
        assertTrue(actual.contains(factory.instructions().gotoTable(TableId.of(1))));
        assertTrue(actual.contains(factory.instructions().buildMeter()
                .setMeterId(2)
                .build()));
        assertTrue(actual.contains(factory.instructions().buildWriteMetadata()
                .setMetadata(U64.of(123))
                .setMetadataMask(U64.of(234))
                .build()));

        OFInstructionApplyActions applyActionsInstruction = (OFInstructionApplyActions) actual.get(3);
        assertEquals(12, applyActionsInstruction.getActions().size());
        List<OFAction> expectedActions = buildActions(factory);
        assertTrue(applyActionsInstruction.getActions().containsAll(expectedActions));
    }

    private static List<OFAction> buildActions(OFFactory factory) {
        List<OFAction> actions = new ArrayList<>();
        actions.add(factory.actions().group(OFGroup.of(3)));
        actions.add(factory.actions().buildOutput()
                .setPort(OFPort.CONTROLLER)
                .build());
        actions.add(factory.actions().popVlan());
        actions.add(factory.actions().pushVlan(EthType.VLAN_FRAME));
        actions.add(factory.actions().noviflowPopVxlanTunnel());
        actions.add(factory.actions().kildaPopVxlanField());
        actions.add(factory.actions().buildNoviflowPushVxlanTunnel()
                .setEthSrc(MacAddress.of(11))
                .setEthDst(MacAddress.of(22))
                .setIpv4Src(IPv4Address.of(33))
                .setIpv4Dst(IPv4Address.of(44))
                .setUdpSrc(55)
                .setVni(66)
                .build());
        actions.add(factory.actions().buildKildaPushVxlanField()
                .setEthSrc(MacAddress.of(111))
                .setEthDst(MacAddress.of(222))
                .setIpv4Src(IPv4Address.of(333))
                .setIpv4Dst(IPv4Address.of(444))
                .setUdpSrc(555)
                .setVni(666)
                .build());
        actions.add(factory.actions().setField(factory.oxms().udpDst(TransportPort.of(7))));
        actions.add(factory.actions().buildNoviflowCopyField()
                .setNBits(1)
                .setSrcOffset(2)
                .setDstOffset(3)
                .setOxmSrcHeader(factory.oxms().buildEthSrc().getTypeLen())
                .setOxmDstHeader(factory.oxms().buildEthDst().getTypeLen())
                .build());
        actions.add(factory.actions().buildNoviflowSwapField()
                .setNBits(11)
                .setSrcOffset(22)
                .setDstOffset(33)
                .setOxmSrcHeader(factory.oxms().buildEthSrc().getTypeLen())
                .setOxmDstHeader(factory.oxms().buildEthDst().getTypeLen())
                .build());
        actions.add(factory.actions().buildKildaSwapField()
                .setNBits(111)
                .setSrcOffset(222)
                .setDstOffset(333)
                .setOxmSrcHeader(factory.oxms().buildEthSrc().getTypeLen())
                .setOxmDstHeader(factory.oxms().buildEthDst().getTypeLen())
                .build());
        return actions;
    }

    private static List<Action> buildActions() {
        List<Action> actions = new ArrayList<>();
        actions.add(new GroupAction(new GroupId(3)));
        actions.add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));
        actions.add(new PopVlanAction());
        actions.add(new PushVlanAction());
        actions.add(new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW));
        actions.add(new PopVxlanAction(ActionType.POP_VXLAN_OVS));
        actions.add(PushVxlanAction.builder()
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .srcMacAddress(buildMacAddress("00:00:00:00:00:0b"))
                .dstMacAddress(buildMacAddress("00:00:00:00:00:16"))
                .srcIpv4Address(buildIPv4Address("0.0.0.33"))
                .dstIpv4Address(buildIPv4Address("0.0.0.44"))
                .udpSrc(55)
                .vni(66)
                .build());
        actions.add(PushVxlanAction.builder()
                .type(ActionType.PUSH_VXLAN_OVS)
                .srcMacAddress(buildMacAddress("00:00:00:00:00:6f"))
                .dstMacAddress(buildMacAddress("00:00:00:00:00:de"))
                .srcIpv4Address(buildIPv4Address("0.0.1.77"))
                .dstIpv4Address(buildIPv4Address("0.0.1.188"))
                .udpSrc(555)
                .vni(666)
                .build());
        actions.add(SetFieldAction.builder()
                .field(Field.UDP_DST)
                .value(7)
                .build());
        actions.add(CopyFieldAction.builder()
                .numberOfBits(1)
                .srcOffset(2)
                .dstOffset(3)
                .oxmSrcHeader(OpenFlowOxms.ETH_SRC)
                .oxmDstHeader(OpenFlowOxms.ETH_DST)
                .build());
        actions.add(SwapFieldAction.builder()
                .type(ActionType.NOVI_SWAP_FIELD)
                .numberOfBits(11)
                .srcOffset(22)
                .dstOffset(33)
                .oxmSrcHeader(OpenFlowOxms.ETH_SRC)
                .oxmDstHeader(OpenFlowOxms.ETH_DST)
                .build());
        actions.add(SwapFieldAction.builder()
                .type(ActionType.KILDA_SWAP_FIELD)
                .numberOfBits(111)
                .srcOffset(222)
                .dstOffset(333)
                .oxmSrcHeader(OpenFlowOxms.ETH_SRC)
                .oxmDstHeader(OpenFlowOxms.ETH_DST)
                .build());
        return actions;
    }

    private static org.openkilda.model.MacAddress buildMacAddress(String mac) {
        return new org.openkilda.model.MacAddress(mac);
    }

    private static org.openkilda.model.IPv4Address buildIPv4Address(String ip) {
        return new org.openkilda.model.IPv4Address(ip);
    }
}
