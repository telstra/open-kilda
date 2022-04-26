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

package org.openkilda.floodlight.converter.rulemanager;

import static java.lang.String.format;

import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.Instructions.InstructionsBuilder;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
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
import org.openkilda.rulemanager.action.SwapFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionKildaPopVxlanField;
import org.projectfloodlight.openflow.protocol.action.OFActionKildaPushVxlanField;
import org.projectfloodlight.openflow.protocol.action.OFActionKildaSwapField;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowCopyField;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowPopVxlanTunnel;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowPushVxlanTunnel;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowSwapField;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanVid;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public class OfInstructionsConverter {
    public static final OfInstructionsConverter INSTANCE = Mappers.getMapper(OfInstructionsConverter.class);

    /**
     * Convert instructions.
     */
    public Instructions convertToRuleManagerInstructions(List<OFInstruction> ofInstructions) {
        InstructionsBuilder builder = Instructions.builder();
        for (OFInstruction ofInstruction : ofInstructions) {
            if (ofInstruction instanceof OFInstructionApplyActions) {
                List<OFAction> ofActions = ((OFInstructionApplyActions) ofInstruction).getActions();
                List<Action> actions = ofActions.stream().map(this::convertToRuleManagerAction)
                        .collect(Collectors.toList());
                builder.applyActions(actions);
            } else if (ofInstruction instanceof OFInstructionWriteActions) {
                List<OFAction> ofActions = ((OFInstructionWriteActions) ofInstruction).getActions();
                Set<Action> actions = ofActions.stream().map(this::convertToRuleManagerAction)
                        .collect(Collectors.toSet());
                builder.writeActions(actions);
            } else if (ofInstruction instanceof OFInstructionMeter) {
                final long meterId = ((OFInstructionMeter) ofInstruction).getMeterId();
                builder.goToMeter(new MeterId(meterId));
            } else if (ofInstruction instanceof OFInstructionGotoTable) {
                final short tableId = ((OFInstructionGotoTable) ofInstruction).getTableId().getValue();
                builder.goToTable(OfTable.fromInt(tableId));
            } else if (ofInstruction instanceof OFInstructionWriteMetadata) {
                final long metadata = ((OFInstructionWriteMetadata) ofInstruction).getMetadata().getValue();
                final long metadataMask = ((OFInstructionWriteMetadata) ofInstruction).getMetadataMask().getValue();
                builder.writeMetadata(new OfMetadata(metadata, metadataMask));
            }

        }
        return builder.build();
    }

    /**
     * Converts action.
     */
    public Action convertToRuleManagerAction(OFAction action) {
        switch (action.getType()) {
            case PUSH_VLAN:
                return new PushVlanAction();
            case POP_VLAN:
                return new PopVlanAction();
            case METER:
                OFActionMeter meterAction = (OFActionMeter) action;
                MeterId meterId = new MeterId(meterAction.getMeterId());
                return new MeterAction(meterId);
            case GROUP:
                OFActionGroup groupAction = (OFActionGroup) action;
                GroupId groupId = new GroupId(groupAction.getGroup().getGroupNumber());
                return new GroupAction(groupId);
            case OUTPUT:
                OFActionOutput outputAction = (OFActionOutput) action;
                PortNumber portNumber = convertPort(outputAction.getPort());
                return new PortOutAction(portNumber);
            case EXPERIMENTER:
                if (action instanceof OFActionNoviflowPushVxlanTunnel) {
                    OFActionNoviflowPushVxlanTunnel pushNoviVxlan = (OFActionNoviflowPushVxlanTunnel) action;
                    return PushVxlanAction.builder()
                            .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                            .vni((int) pushNoviVxlan.getVni())
                            .srcMacAddress(convertMac(pushNoviVxlan.getEthSrc()))
                            .dstMacAddress(convertMac(pushNoviVxlan.getEthDst()))
                            .srcIpv4Address(convertIPv4Address(pushNoviVxlan.getIpv4Src()))
                            .dstIpv4Address(convertIPv4Address(pushNoviVxlan.getIpv4Dst()))
                            .udpSrc(pushNoviVxlan.getUdpSrc())
                            .build();
                } else if (action instanceof OFActionKildaPushVxlanField) {
                    OFActionKildaPushVxlanField pushKildaVxlan = (OFActionKildaPushVxlanField) action;
                    return PushVxlanAction.builder()
                            .type(ActionType.PUSH_VXLAN_OVS)
                            .vni((int) pushKildaVxlan.getVni())
                            .srcMacAddress(convertMac(pushKildaVxlan.getEthSrc()))
                            .dstMacAddress(convertMac(pushKildaVxlan.getEthDst()))
                            .srcIpv4Address(convertIPv4Address(pushKildaVxlan.getIpv4Src()))
                            .dstIpv4Address(convertIPv4Address(pushKildaVxlan.getIpv4Dst()))
                            .udpSrc(pushKildaVxlan.getUdpSrc())
                            .build();
                } else if (action instanceof OFActionNoviflowCopyField) {
                    OFActionNoviflowCopyField copyField = (OFActionNoviflowCopyField) action;
                    return CopyFieldAction.builder()
                            .numberOfBits(copyField.getNBits())
                            .srcOffset(copyField.getSrcOffset())
                            .dstOffset(copyField.getDstOffset())
                            .oxmSrcHeader(convertOxmHeader(copyField.getOxmSrcHeader()))
                            .oxmDstHeader(convertOxmHeader(copyField.getOxmDstHeader()))
                            .build();
                } else if (action instanceof OFActionNoviflowSwapField) {
                    OFActionNoviflowSwapField swapField = (OFActionNoviflowSwapField) action;
                    return SwapFieldAction.builder()
                            .type(ActionType.NOVI_SWAP_FIELD)
                            .numberOfBits(swapField.getNBits())
                            .srcOffset(swapField.getSrcOffset())
                            .dstOffset(swapField.getDstOffset())
                            .oxmSrcHeader(convertOxmHeader(swapField.getOxmSrcHeader()))
                            .oxmDstHeader(convertOxmHeader(swapField.getOxmDstHeader()))
                            .build();
                } else if (action instanceof OFActionKildaSwapField) {
                    OFActionKildaSwapField swapField = (OFActionKildaSwapField) action;
                    return SwapFieldAction.builder()
                            .type(ActionType.KILDA_SWAP_FIELD)
                            .numberOfBits(swapField.getNBits())
                            .srcOffset(swapField.getSrcOffset())
                            .dstOffset(swapField.getDstOffset())
                            .oxmSrcHeader(convertOxmHeader(swapField.getOxmSrcHeader()))
                            .oxmDstHeader(convertOxmHeader(swapField.getOxmDstHeader()))
                            .build();
                } else if (action instanceof OFActionNoviflowPopVxlanTunnel) {
                    return new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW);
                } else if (action instanceof OFActionKildaPopVxlanField) {
                    return new PopVxlanAction(ActionType.POP_VXLAN_OVS);
                } else {
                    throw new IllegalStateException(format("Unknown experimenter action %s", action.getType()));
                }
            case SET_FIELD:
                OFActionSetField setFieldAction = (OFActionSetField) action;
                return convertOxm(setFieldAction.getField());
            case SET_VLAN_VID:
                OFActionSetVlanVid setVlanVid = (OFActionSetVlanVid) action;
                return SetFieldAction.builder()
                        .field(Field.VLAN_VID)
                        .value(setVlanVid.getVlanVid().getVlan())
                        .build();
            default:
                throw new IllegalStateException(format("Unknown action type %s", action.getType()));
        }
    }

    /**
     * Convert instructions.
     */
    public List<OFInstruction> convertInstructions(Instructions instructions, OFFactory ofFactory) {
        List<OFInstruction> result = new ArrayList<>();
        if (instructions.getGoToTable() != null) {
            result.add(ofFactory.instructions().gotoTable(TableId.of(instructions.getGoToTable().getTableId())));
        }
        if (instructions.getGoToMeter() != null) {
            result.add(ofFactory.instructions().buildMeter()
                    .setMeterId(instructions.getGoToMeter().getValue())
                    .build());
        }
        if (instructions.getWriteMetadata() != null) {
            result.add(ofFactory.instructions().buildWriteMetadata()
                    .setMetadata(U64.of(instructions.getWriteMetadata().getValue()))
                    .setMetadataMask(U64.of(instructions.getWriteMetadata().getMask()))
                    .build());
        }
        if (instructions.getApplyActions() != null) {
            List<OFAction> applyActions = convertActions(instructions.getApplyActions(), ofFactory);
            result.add(ofFactory.instructions().applyActions(applyActions));
        }
        return result;
    }

    List<OFAction> convertActions(Collection<Action> actions, OFFactory ofFactory) {
        return actions.stream()
                .map(action -> convertAction(action, ofFactory))
                .collect(Collectors.toList());
    }

    private OFAction convertAction(Action action, OFFactory ofFactory) {
        switch (action.getType()) {
            case GROUP:
                GroupAction groupAction = (GroupAction) action;
                return ofFactory.actions()
                        .group(OFGroup.of(groupAction.getGroupId().intValue()));
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                return ofFactory.actions().buildOutput()
                        .setPort(convertPort(portOutAction.getPortNumber()))
                        .setMaxLen(0xFFFFFFFF)
                        .build();
            case POP_VLAN:
                return ofFactory.actions().popVlan();
            case PUSH_VLAN:
                return ofFactory.actions().pushVlan(EthType.VLAN_FRAME);
            case POP_VXLAN_NOVIFLOW:
                return ofFactory.actions().noviflowPopVxlanTunnel();
            case POP_VXLAN_OVS:
                return ofFactory.actions().kildaPopVxlanField();
            case PUSH_VXLAN_NOVIFLOW:
            case PUSH_VXLAN_OVS:
                PushVxlanAction pushVxlanAction = (PushVxlanAction) action;
                if (pushVxlanAction.getType().equals(ActionType.PUSH_VXLAN_OVS)) {
                    return ofFactory.actions().buildKildaPushVxlanField()
                            .setEthDst(MacAddress.of(pushVxlanAction.getDstMacAddress().toLong()))
                            .setEthSrc(MacAddress.of(pushVxlanAction.getSrcMacAddress().toLong()))
                            .setIpv4Src(IPv4Address.of(pushVxlanAction.getSrcIpv4Address().getAddress()))
                            .setIpv4Dst(IPv4Address.of(pushVxlanAction.getDstIpv4Address().getAddress()))
                            .setUdpSrc(pushVxlanAction.getUdpSrc())
                            .setVni(pushVxlanAction.getVni())
                            .build();
                } else {
                    return ofFactory.actions().buildNoviflowPushVxlanTunnel()
                            .setEthDst(MacAddress.of(pushVxlanAction.getDstMacAddress().toLong()))
                            .setEthSrc(MacAddress.of(pushVxlanAction.getSrcMacAddress().toLong()))
                            .setIpv4Src(IPv4Address.of(pushVxlanAction.getSrcIpv4Address().getAddress()))
                            .setIpv4Dst(IPv4Address.of(pushVxlanAction.getDstIpv4Address().getAddress()))
                            .setUdpSrc(pushVxlanAction.getUdpSrc())
                            .setVni(pushVxlanAction.getVni())
                            // Set to 0x01 indicating tunnel data is present
                            // (i.e. we are passing l2 and l3 headers in this action)
                            .setFlags((short) 0x01)
                            .build();
                }
            case METER:
                MeterAction meterAction = (MeterAction) action;
                return ofFactory.actions().meter(meterAction.getMeterId().getValue());
            case NOVI_COPY_FIELD:
                CopyFieldAction copyFieldAction = (CopyFieldAction) action;
                return ofFactory.actions().buildNoviflowCopyField()
                        .setNBits(copyFieldAction.getNumberOfBits())
                        .setDstOffset(copyFieldAction.getDstOffset())
                        .setSrcOffset(copyFieldAction.getSrcOffset())
                        .setOxmDstHeader(getOxmsForCopyAndSwap(ofFactory, copyFieldAction.getOxmDstHeader()))
                        .setOxmSrcHeader(getOxmsForCopyAndSwap(ofFactory, copyFieldAction.getOxmSrcHeader()))
                        .build();
            case NOVI_SWAP_FIELD:
            case KILDA_SWAP_FIELD:
                SwapFieldAction swapFieldAction = (SwapFieldAction) action;
                if (swapFieldAction.getType().equals(ActionType.NOVI_SWAP_FIELD)) {
                    return ofFactory.actions().buildNoviflowSwapField()
                            .setNBits(swapFieldAction.getNumberOfBits())
                            .setDstOffset(swapFieldAction.getDstOffset())
                            .setSrcOffset(swapFieldAction.getSrcOffset())
                            .setOxmSrcHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmSrcHeader()))
                            .setOxmDstHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmDstHeader()))
                            .build();
                } else {
                    return ofFactory.actions().buildKildaSwapField()
                            .setNBits(swapFieldAction.getNumberOfBits())
                            .setDstOffset(swapFieldAction.getDstOffset())
                            .setSrcOffset(swapFieldAction.getSrcOffset())
                            .setOxmSrcHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmSrcHeader()))
                            .setOxmDstHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmDstHeader()))
                            .build();
                }
            case SET_FIELD:
                SetFieldAction setFieldAction = (SetFieldAction) action;
                return ofFactory.actions().setField(getOxm(ofFactory, setFieldAction));
            default:
                throw new IllegalStateException(format("Unknown action type %s", action.getType()));
        }
    }

    private OFOxm<?> getOxm(OFFactory ofFactory, SetFieldAction action) {
        switch (action.getField()) {
            case ETH_DST:
                return ofFactory.oxms().ethDst(MacAddress.of(action.getValue()));
            case ETH_SRC:
                return ofFactory.oxms().ethSrc(MacAddress.of(action.getValue()));
            case IN_PORT:
                return ofFactory.oxms().inPort(OFPort.of((int) action.getValue()));
            case UDP_DST:
                return ofFactory.oxms().udpDst(TransportPort.of((int) action.getValue()));
            case UDP_SRC:
                return ofFactory.oxms().udpSrc(TransportPort.of((int) action.getValue()));
            case ETH_TYPE:
                return ofFactory.oxms().ethType(EthType.of((int) action.getValue()));
            case IP_PROTO:
                return ofFactory.oxms().ipProto(IpProtocol.of((short) action.getValue()));
            case VLAN_VID:
                return ofFactory.oxms().vlanVid(OFVlanVidMatch.ofVlan((int) action.getValue()));
            case METADATA:
                return ofFactory.oxms().metadata(OFMetadata.of(U64.of(action.getValue())));
            case OVS_VXLAN_VNI:
                return ofFactory.oxms().kildaVxlanVni(U32.of(action.getValue()));
            case NOVIFLOW_TUNNEL_ID:
                return ofFactory.oxms().tunnelId(U64.of(action.getValue()));
            default:
                throw new IllegalArgumentException("Unknown match");
        }
    }

    private long getOxmsForCopyAndSwap(OFFactory factory, OpenFlowOxms oxms) {
        switch (oxms) {
            case ETH_DST:
                return factory.oxms().buildEthDst().getTypeLen();
            case ETH_SRC:
                return factory.oxms().buildEthSrc().getTypeLen();
            case NOVIFLOW_PACKET_OFFSET:
                return factory.oxms().buildNoviflowPacketOffset().getTypeLen();
            case NOVIFLOW_RX_TIMESTAMP:
                return factory.oxms().buildNoviflowRxtimestamp().getTypeLen();
            case NOVIFLOW_TX_TIMESTAMP:
                return factory.oxms().buildNoviflowTxtimestamp().getTypeLen();
            case NOVIFLOW_UDP_PAYLOAD_OFFSET:
                return factory.oxms().buildNoviflowUpdPayload().getTypeLen();
            default:
                throw new IllegalArgumentException(format("Unknown oxm %s", oxms));
        }
    }

    private PortNumber convertPort(OFPort ofPort) {
        if (ofPort.equals(OFPort.LOCAL)) {
            return new PortNumber(SpecialPortType.LOCAL);
        } else if (ofPort.equals(OFPort.CONTROLLER)) {
            return new PortNumber(SpecialPortType.CONTROLLER);
        } else if (ofPort.equals(OFPort.IN_PORT)) {
            return new PortNumber(SpecialPortType.IN_PORT);
        } else if (ofPort.equals(OFPort.ALL)) {
            return new PortNumber(SpecialPortType.ALL);
        } else if (ofPort.equals(OFPort.FLOOD)) {
            return new PortNumber(SpecialPortType.FLOOD);
        } else {
            return new PortNumber(ofPort.getPortNumber());
        }
    }

    private OFPort convertPort(PortNumber portNumber) {
        if (portNumber.getPortNumber() != 0) {
            return OFPort.of(portNumber.getPortNumber());
        } else {
            switch (portNumber.getPortType()) {
                case LOCAL:
                    return OFPort.LOCAL;
                case CONTROLLER:
                    return OFPort.CONTROLLER;
                case IN_PORT:
                    return OFPort.IN_PORT;
                default:
                    throw new IllegalStateException(format("Unknown port type %s", portNumber.getPortType()));
            }
        }
    }

    private org.openkilda.model.MacAddress convertMac(MacAddress macAddress) {
        return new org.openkilda.model.MacAddress(macAddress.toString());
    }

    private org.openkilda.model.IPv4Address convertIPv4Address(IPv4Address address) {
        return new org.openkilda.model.IPv4Address(address.toString());
    }

    private SetFieldAction convertOxm(OFOxm<?> field) {
        if (field.getMatchField() == MatchField.ETH_SRC) {
            MacAddress ethSrcMatch = (MacAddress) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.ETH_SRC)
                    .value(ethSrcMatch.getLong())
                    .build();
        } else if (field.getMatchField() == MatchField.ETH_DST) {
            MacAddress ethDstMatch = (MacAddress) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.ETH_DST)
                    .value(ethDstMatch.getLong())
                    .build();
        } else if (field.getMatchField() == MatchField.IN_PORT) {
            OFPort inPortMatch = (OFPort) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.IN_PORT)
                    .value(inPortMatch.getPortNumber())
                    .build();
        } else if (field.getMatchField() == MatchField.UDP_SRC) {
            TransportPort udpSrcMatch = (TransportPort) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.UDP_SRC)
                    .value(udpSrcMatch.getPort())
                    .build();
        } else if (field.getMatchField() == MatchField.UDP_DST) {
            TransportPort udpDstMatch = (TransportPort) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.UDP_DST)
                    .value(udpDstMatch.getPort())
                    .build();
        } else if (field.getMatchField() == MatchField.ETH_TYPE) {
            EthType ethTypeMatch = (EthType) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.ETH_TYPE)
                    .value(ethTypeMatch.getValue())
                    .build();
        } else if (field.getMatchField() == MatchField.IP_PROTO) {
            IpProtocol ipProtocolMatch = (IpProtocol) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.IP_PROTO)
                    .value(ipProtocolMatch.getIpProtocolNumber())
                    .build();
        } else if (field.getMatchField() == MatchField.VLAN_VID) {
            OFVlanVidMatch vlanMatch = (OFVlanVidMatch) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.VLAN_VID)
                    .value(vlanMatch.getVlan())
                    .build();
        } else if (field.getMatchField() == MatchField.TUNNEL_ID) {
            U64 tunnelIdMatch = (U64) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.NOVIFLOW_TUNNEL_ID)
                    .value(tunnelIdMatch.getValue())
                    .build();
        } else if (field.getMatchField() == MatchField.KILDA_VXLAN_VNI) {
            U32 kildaVxlanVni = (U32) field.getValue();
            return SetFieldAction.builder()
                    .field(Field.OVS_VXLAN_VNI)
                    .value(kildaVxlanVni.getValue())
                    .build();
        } else {
            throw new IllegalArgumentException(format("Unknown match %s", field.getMatchField()));
        }
    }

    private OpenFlowOxms convertOxmHeader(long value) {
        if (value == 2147485702L) { // value from OFOxmEthSrcVer13
            return OpenFlowOxms.ETH_SRC;
        } else if (value == 2147485190L) { // value from OFOxmEthDstVer13
            return OpenFlowOxms.ETH_DST;
        } else if (value == 4294905860L) { // value from OFOxmNoviflowPacketOffsetVer13
            return OpenFlowOxms.NOVIFLOW_PACKET_OFFSET;
        } else if (value == 4294902276L) { // value from OFOxmNoviflowUpdPayloadVer13
            return OpenFlowOxms.NOVIFLOW_UDP_PAYLOAD_OFFSET;
        } else if (value == 4294904836L) { // value from OFOxmNoviflowRxtimestampVer13
            return OpenFlowOxms.NOVIFLOW_RX_TIMESTAMP;
        } else if (value == 4294905348L) { // value from OFOxmNoviflowTxtimestampVer13
            return OpenFlowOxms.NOVIFLOW_TX_TIMESTAMP;
        } else {
            throw new IllegalArgumentException(format("Unknown Oxm header %s", value));
        }
    }
}
