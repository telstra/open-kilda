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
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowCopyField;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
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
import java.util.Collections;
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
                List<Action> actions = ofActions.stream().map(ofAction -> convertToRuleManagerAction(ofAction))
                        .collect(Collectors.toList());
                builder.applyActions(actions);
            } else if (ofInstruction instanceof OFInstructionWriteActions) {
                List<OFAction> ofActions = ((OFInstructionWriteActions) ofInstruction).getActions();
                Set<Action> actions = ofActions.stream().map(ofAction -> convertToRuleManagerAction(ofAction))
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


    Action convertToRuleManagerAction(OFAction action) {
        switch (action.getType()) {
            case PUSH_VLAN:
                return PushVlanAction.builder().build();
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


            // todo (rule-manager-fl-integration): add other port types
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
            List<OFAction> applyActions =  convertActions(instructions.getApplyActions(), ofFactory);
            result.add(ofFactory.instructions().applyActions(applyActions));
        }
        return result;
    }

    List<OFAction> convertActions(Collection<Action> actions, OFFactory ofFactory) {
        return actions.stream()
                .map(action -> convertAction(action, ofFactory))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<OFAction> convertAction(Action action, OFFactory ofFactory) {
        switch (action.getType()) {
            case GROUP:
                GroupAction groupAction = (GroupAction) action;
                OFAction ofGroup = ofFactory.actions()
                        .group(OFGroup.of(groupAction.getGroupId().intValue()));
                return Collections.singletonList(ofGroup);
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                OFAction ofPortOutput = ofFactory.actions().buildOutput()
                        .setPort(convertPort(portOutAction.getPortNumber()))
                        .build();
                return Collections.singletonList(ofPortOutput);
            case POP_VLAN:
                return Collections.singletonList(ofFactory.actions().popVlan());
            case PUSH_VLAN:
                PushVlanAction pushVlanAction = (PushVlanAction) action;
                short vlanId = pushVlanAction.getVlanId();
                List<OFAction> ofActions = new ArrayList<>();
                ofActions.add(ofFactory.actions().pushVlan(EthType.VLAN_FRAME));
                if (vlanId > 0) {
                    OFVlanVidMatch vlanMatch = ofFactory.getVersion() == OFVersion.OF_12
                            ? OFVlanVidMatch.ofRawVid((short) vlanId) : OFVlanVidMatch.ofVlan(vlanId);

                    ofActions.add(ofFactory.actions().setField(ofFactory.oxms().vlanVid(vlanMatch)));
                }
                return ofActions;
            case POP_VXLAN_NOVIFLOW:
                return Collections.singletonList(ofFactory.actions().noviflowPopVxlanTunnel());
            case POP_VXLAN_OVS:
                return Collections.singletonList(ofFactory.actions().kildaPopVxlanField());
            case PUSH_VXLAN_NOVIFLOW:
            case PUSH_VXLAN_OVS:
                PushVxlanAction pushVxlanAction = (PushVxlanAction) action;
                OFAction vxlanAction;
                if (pushVxlanAction.getType().equals(ActionType.PUSH_VXLAN_OVS)) {
                    vxlanAction = ofFactory.actions().buildKildaPushVxlanField()
                            .setEthDst(MacAddress.of(pushVxlanAction.getDstMacAddress().toLong()))
                            .setEthSrc(MacAddress.of(pushVxlanAction.getSrcMacAddress().toLong()))
                            .setIpv4Src(IPv4Address.of(pushVxlanAction.getSrcIpv4Address().getAddress()))
                            .setIpv4Dst(IPv4Address.of(pushVxlanAction.getDstIpv4Address().getAddress()))
                            .setUdpSrc(pushVxlanAction.getUdpSrc())
                            .setVni(pushVxlanAction.getVni())
                            .build();
                } else {
                    vxlanAction = ofFactory.actions().buildNoviflowPushVxlanTunnel()
                            .setEthDst(MacAddress.of(pushVxlanAction.getDstMacAddress().toLong()))
                            .setEthSrc(MacAddress.of(pushVxlanAction.getSrcMacAddress().toLong()))
                            .setIpv4Src(IPv4Address.of(pushVxlanAction.getSrcIpv4Address().getAddress()))
                            .setIpv4Dst(IPv4Address.of(pushVxlanAction.getDstIpv4Address().getAddress()))
                            .setUdpSrc(pushVxlanAction.getUdpSrc())
                            .setVni(pushVxlanAction.getVni())
                            .build();
                }
                return Collections.singletonList(vxlanAction);
            case METER:
                MeterAction meterAction = (MeterAction) action;
                OFActionMeter meter = ofFactory.actions().meter(meterAction.getMeterId().getValue());
                return Collections.singletonList(meter);
            case NOVI_COPY_FIELD:
                CopyFieldAction copyFieldAction = (CopyFieldAction) action;
                OFActionNoviflowCopyField copyField = ofFactory.actions().buildNoviflowCopyField()
                        .setNBits(copyFieldAction.getNumberOfBits())
                        .setDstOffset(copyFieldAction.getDstOffset())
                        .setSrcOffset(copyFieldAction.getSrcOffset())
                        .setOxmDstHeader(getOxmsForCopyAndSwap(ofFactory, copyFieldAction.getOxmDstHeader()))
                        .setOxmSrcHeader(getOxmsForCopyAndSwap(ofFactory, copyFieldAction.getOxmSrcHeader()))
                        .build();
                return Collections.singletonList(copyField);
            case NOVI_SWAP_FIELD:
            case KILDA_SWAP_FIELD:
                SwapFieldAction swapFieldAction = (SwapFieldAction) action;
                OFAction swapField;
                if (swapFieldAction.getType().equals(ActionType.NOVI_SWAP_FIELD)) {
                    swapField = ofFactory.actions().buildNoviflowSwapField()
                            .setNBits(swapFieldAction.getNumberOfBits())
                            .setDstOffset(swapFieldAction.getDstOffset())
                            .setSrcOffset(swapFieldAction.getSrcOffset())
                            .setOxmSrcHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmSrcHeader()))
                            .setOxmDstHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmDstHeader()))
                            .build();
                } else {
                    swapField = ofFactory.actions().buildKildaSwapField()
                            .setNBits(swapFieldAction.getNumberOfBits())
                            .setDstOffset(swapFieldAction.getDstOffset())
                            .setSrcOffset(swapFieldAction.getSrcOffset())
                            .setOxmSrcHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmSrcHeader()))
                            .setOxmDstHeader(getOxmsForCopyAndSwap(ofFactory, swapFieldAction.getOxmDstHeader()))
                            .build();
                }
                return Collections.singletonList(swapField);
            case SET_FIELD:
                SetFieldAction setFieldAction = (SetFieldAction) action;
                return Collections.singletonList(ofFactory.actions().setField(getOxm(ofFactory, setFieldAction)));
            default:
                throw new IllegalStateException(format("Unknown action type %s", action.getType()));
        }
    }

    private OFOxm getOxm(OFFactory ofFactory, SetFieldAction action) {
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
                factory.oxms().buildEthDst().getTypeLen();
                break;
            case ETH_SRC:
                factory.oxms().buildEthSrc().getTypeLen();
                break;
            case NOVIFLOW_PACKET_OFFSET:
                factory.oxms().buildNoviflowPacketOffset().getTypeLen();
                break;
            case NOVIFLOW_RX_TIMESTAMP:
                factory.oxms().buildNoviflowRxtimestamp().getTypeLen();
                break;
            case NOVIFLOW_TX_TIMESTAMP:
                factory.oxms().buildNoviflowTxtimestamp().getTypeLen();
                break;
            case NOVIFLOW_UDP_PAYLOAD_OFFSET:
                factory.oxms().buildNoviflowUpdPayload().getTypeLen();
                break;
            default:
                throw new IllegalArgumentException("Unknown oxm");
        }
        return 0;
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
}
